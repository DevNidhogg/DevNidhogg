import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Read-only signature-based recovery for contiguous files. */
public final class FileCarving {
    record Format(String extension, byte[] header, byte[] footer) {}
    static byte[] hex(String s){return HexFormat.of().parseHex(s);}
    static final Format[] FORMATS={new Format("png",hex("89504e470d0a1a0a"),null),new Format("jpg",hex("ffd8ff"),hex("ffd9")),new Format("pdf",hex("255044462d"),hex("2525454f46")),new Format("zip",hex("504b0304"),hex("504b0506"))};
    static final long MAX=Math.clamp(Long.getLong("carving.maxMiB",256L),1L,4096L)*1024*1024;
    static final int LIMIT=1000;
    static boolean at(RandomAccessFile f,long pos,byte[] sig)throws IOException{
        if(pos<0 || pos>f.length()-sig.length)return false;f.seek(pos);
        for(byte b:sig)if(f.read()!=Byte.toUnsignedInt(b))return false;return true;
    }
    /** Parse JPEG marker structure, skipping APP payloads and handling stuffed entropy bytes. */
    static long jpegEnd(RandomAccessFile f,long start,long bound)throws IOException{
        f.seek(start+2); InputStream raw=new InputStream(){public int read()throws IOException{return f.read();}public int read(byte[] b,int off,int len)throws IOException{return f.read(b,off,len);}};
        BufferedInputStream in=new BufferedInputStream(raw,65536);long pos=start+2;boolean scan=false,sof=false,sos=false;
        while(pos<bound){int b=in.read();pos++;if(b<0)return -1;if(b!=255){if(scan)continue;return -1;}
            int m;do{m=in.read();pos++;}while(m==255&&pos<bound);if(m<0||pos>bound)return -1;
            if(scan&&(m==0||m>=0xd0&&m<=0xd7))continue;
            if(m==0xd9)return sof&&sos?pos:-1;
            if(m==0||m==0xd8||m>=0xd0&&m<=0xd7)return -1;
            if(m==1)continue;
            int hi=in.read(),lo=in.read();pos+=2;if(hi<0||lo<0)return -1;int n=(hi<<8)|lo;if(n<2||n-2>bound-pos)return -1;
            if(m>=0xc0&&m<=0xcf&&m!=0xc4&&m!=0xc8&&m!=0xcc){if(n<8)return -1;sof=true;}
            in.skipNBytes(n-2);pos+=n-2;scan=m==0xda;if(scan)sos=true;
        }return -1;
    }
    static long end(RandomAccessFile f,long start,Format format)throws IOException{
        long bound=start+Math.min(MAX,f.length()-start);
        if(format.extension.equals("jpg"))return jpegEnd(f,start,bound);
        if(format.extension.equals("png")){
            long p=start+8;boolean first=true,idat=false;
            while(p+12<=bound){
                f.seek(p);long n=Integer.toUnsignedLong(f.readInt());byte[] type=new byte[4];f.readFully(type);
                String t=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
                if(n>bound-p-12 || (first && (!t.equals("IHDR") || n!=13)))return -1;
                CRC32 crc=new CRC32();crc.update(type);byte[] buf=new byte[8192];long left=n;
                while(left>0){int take=(int)Math.min(left,buf.length);f.readFully(buf,0,take);crc.update(buf,0,take);left-=take;}
                if(crc.getValue()!=Integer.toUnsignedLong(f.readInt()))return -1;
                p+=12+n;first=false;if(t.equals("IDAT"))idat=true;
                if(t.equals("IEND"))return n==0 && idat?p:-1;
            }return -1;
        }
        byte[] footer=format.footer;int matched=0;long cursor=start+format.header.length;byte[] block=new byte[65536];
        while(cursor<bound){
            f.seek(cursor);int read=f.read(block,0,(int)Math.min(block.length,bound-cursor));if(read<0)break;
            for(int idx=0;idx<read;idx++){int b=Byte.toUnsignedInt(block[idx]);
            matched=b==Byte.toUnsignedInt(footer[matched])?matched+1:(b==Byte.toUnsignedInt(footer[0])?1:0);
            if(matched==footer.length){long pos=cursor+idx+1;
                if(!format.extension.equals("zip"))return pos;
                if(pos+18<=bound){byte[] e=new byte[18];f.seek(pos);f.readFully(e);
                    java.nio.ByteBuffer v=java.nio.ByteBuffer.wrap(e).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    int disk=Short.toUnsignedInt(v.getShort()),cdDisk=Short.toUnsignedInt(v.getShort());
                    int entries=Short.toUnsignedInt(v.getShort()),total=Short.toUnsignedInt(v.getShort());
                    long size=Integer.toUnsignedLong(v.getInt()),offset=Integer.toUnsignedLong(v.getInt());int comment=Short.toUnsignedInt(v.getShort());
                    if(disk==0 && cdDisk==0 && entries==total && offset+size==pos-4-start && pos+18+comment<=bound)return pos+18+comment;
                }matched=0;
            }}cursor+=read;
        }return -1;
    }
    static int recover(Path source,Path out)throws Exception{
        if(!Files.isRegularFile(source))throw new IOException("Select a regular disk image, not a live device");
        Files.createDirectories(out);Path report=out.resolve("recovery-"+UUID.randomUUID()+".tsv");int count=0,attempts=0;
        try(RandomAccessFile f=new RandomAccessFile(source.toFile(),"r");InputStream scan=new BufferedInputStream(Files.newInputStream(source));BufferedWriter log=Files.newBufferedWriter(report,StandardOpenOption.CREATE_NEW)){
            log.write("offset\tbytes\tformat\tsha256\tvalidation\tfile\n");
            byte[] window=new byte[8];long pos=0;int b;
            while((b=scan.read())!=-1 && count<LIMIT && attempts<10000){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Scan cancelled");
                window[(int)(pos%8)]=(byte)b;long next=pos+1;
                for(Format fmt:FORMATS){int len=fmt.header.length;if(next<len)continue;long start=next-len;boolean match=true;
                    for(int j=0;j<len;j++)if(window[(int)((start+j)%8)]!=fmt.header[j]){match=false;break;}
                    if(!match)continue;attempts++;long finish=end(f,start,fmt);if(finish<=start)continue;
                    Path dest=out.resolve(String.format(Locale.ROOT,"%016x-%s.%s",start,UUID.randomUUID().toString().substring(0,8),fmt.extension));
                    MessageDigest hash=MessageDigest.getInstance("SHA-256");f.seek(start);byte[] buf=new byte[65536];long left=finish-start;
                    try(OutputStream os=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){while(left>0){int n=(int)Math.min(left,buf.length);f.readFully(buf,0,n);os.write(buf,0,n);hash.update(buf,0,n);left-=n;}}
                    String validation=fmt.extension.equals("png")?"chunk-crc":fmt.extension.equals("jpg")?"marker-structure":"signature-only";
                    if(fmt.extension.equals("zip")){try(ZipFile z=new ZipFile(dest.toFile())){z.size();validation="central-directory";}catch(IOException e){Files.delete(dest);continue;}}
                    log.write(start+"\t"+(finish-start)+"\t"+fmt.extension+"\t"+HexFormat.of().formatHex(hash.digest())+"\t"+validation+"\t"+dest.getFileName()+"\n");count++;
                    scan.skipNBytes(finish-next);pos=finish-1;Arrays.fill(window,(byte)0);break;
                }pos++;
            }
        }
        System.out.println("Recovered: "+count+" | Candidates: "+attempts+" | Report: "+report+(count>=LIMIT || attempts>=10000?" | LIMIT REACHED: scan incomplete":""));return count;
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0 && args[0].equals("--self-test")){
            Path dir=Files.createTempDirectory("carving-test");Path src=dir.resolve("disk.img");byte[] sample=hex("000102255044462d312e340a2525454f4600");Files.write(src,sample);
            if(recover(src,dir.resolve("out"))!=1 || !Arrays.equals(sample,Files.readAllBytes(src)))throw new AssertionError();System.out.println("Carving tests passed");return;
        }
        Scanner in=new Scanner(System.in);System.out.print("Disk image path: ");Path src=Path.of(in.nextLine().strip().replaceAll("^\"|\"$",""));
        System.out.print("Output directory: ");Path out=Path.of(in.nextLine().strip().replaceAll("^\"|\"$",""));recover(src,out);
    }
}
