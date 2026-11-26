import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public final class SmokeTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path d=Files.createTempDirectory("jas-smoke-");try{
            for(String mode:List.of("ascii","mixed","random","greek","cyrillic","hebrew","arabic","custom")){int[] p=PasswordGeneration.pool(mode,"🦉é\u0301\n");check(Arrays.stream(p).distinct().count()==p.length,"duplicate pool");for(int n:List.of(1,32,4096)){String s=PasswordGeneration.generate(n,p);check(s.codePointCount(0,s.length())==n,"length");check(s.codePoints().allMatch(c->Arrays.stream(p).anyMatch(x->x==c)),"pool membership");}}
            check(PasswordGeneration.pool("ascii","").length==94,"ASCII count");
            BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB);image.setRGB(4,5,0xff3288cc);
            Path png=d.resolve("photo.png"),jpg=d.resolve("photo.jpg"),zip=d.resolve("archive.zip");ImageIO.write(image,"png",png.toFile());ImageIO.write(image,"jpg",jpg.toFile());try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(zip))){z.putNextEntry(new ZipEntry("sample.txt"));z.write("test fixture".getBytes());z.closeEntry();}
            ByteArrayOutputStream disk=new ByteArrayOutputStream();disk.write(new byte[65533]);for(Path f:List.of(png,jpg,zip)){disk.write(Files.readAllBytes(f));disk.write(new byte[13]);}Path raw=d.resolve("disk.raw");byte[] original=disk.toByteArray();Files.write(raw,original);Path output=d.resolve("recovered");check(FileCarving.recover(raw,output)==3,"three real formats");check(Arrays.equals(original,Files.readAllBytes(raw)),"source unchanged");try(var ls=Files.list(output)){List<Path> files=ls.toList();for(Path f:List.of(png,jpg,zip)){String ext=MetadataScore.ext(f);Path recovered=files.stream().filter(x->MetadataScore.ext(x).equals(ext)).findFirst().orElseThrow();check(Arrays.equals(Files.readAllBytes(f),Files.readAllBytes(recovered)),"recovery byte equality "+ext);}}
            byte[] bytes=Files.readAllBytes(png);ByteArrayOutputStream withText=new ByteArrayOutputStream();withText.write(bytes,0,33);byte[] type="tEXt".getBytes(),text="Author\0synthetic".getBytes();CRC32 crc=new CRC32();crc.update(type);crc.update(text);DataOutputStream data=new DataOutputStream(withText);data.writeInt(text.length);data.write(type);data.write(text);data.writeInt((int)crc.getValue());data.write(bytes,33,bytes.length-33);Path tagged=d.resolve("tagged.png"),clean=d.resolve("clean.png");Files.write(tagged,withText.toByteArray());check(Desktop.stripImage(tagged,clean)==1,"PNG metadata removed");check(Arrays.equals(bytes,Files.readAllBytes(clean)),"PNG original bytes restored");check(Arrays.equals(withText.toByteArray(),Files.readAllBytes(tagged)),"tagged source unchanged");
            byte[] j=Files.readAllBytes(jpg);ByteArrayOutputStream jpeg=new ByteArrayOutputStream();jpeg.write(j,0,2);byte[] tag="Exif\0\0synthetic fixture".getBytes();DataOutputStream jd=new DataOutputStream(jpeg);jd.writeShort(0xffe1);jd.writeShort(tag.length+2);jd.write(tag);jd.write(j,2,j.length-2);Path taggedJ=d.resolve("tagged.jpg"),cleanJ=d.resolve("clean.jpg");Files.write(taggedJ,jpeg.toByteArray());check(Desktop.stripImage(taggedJ,cleanJ)==1,"JPEG APP1 removed");check(Arrays.equals(j,Files.readAllBytes(cleanJ)),"JPEG bytes restored");
            try{Desktop.stripImage(tagged,clean);throw new AssertionError("overwrite accepted");}catch(IOException expected){}Path corrupt=d.resolve("corrupt.png");byte[] damaged=withText.toByteArray();damaged[45]^=1;Files.write(corrupt,damaged);Path reject=d.resolve("reject.png");try{Desktop.stripImage(corrupt,reject);throw new AssertionError("CRC corruption accepted");}catch(IOException expected){check(!Files.exists(reject),"partial output exposed");}
            System.out.println("Smoke tests passed: 24 password configurations, three real format recoveries, exact byte equality, PNG/JPEG removal, overwrite rejection, CRC rejection.");
        }finally{try(var paths=Files.walk(d)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
