import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Metadata inventory, explicit field edits and copy-only export. */
public final class MetadataScore {
    record Field(String key,String value,int risk){}
    static final String CORE="docProps/core.xml";
    static final Set<String> IMAGE=Set.of("jpg","jpeg","png","tif","tiff","webp");
    static final Set<String> OFFICE=Set.of("docx","xlsx","pptx");
    static String ext(Path p){String s=p.getFileName().toString();return s.substring(s.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);}
    static int risk(String key){String k=key.toLowerCase(Locale.ROOT);
        if(k.contains("gps")||k.contains("location")||k.contains("serial")||k.contains("latitude")||k.contains("longitude"))return 30;
        if(k.contains("author")||k.contains("creator")||k.contains("owner")||k.contains("artist")||k.contains("lastmodifiedby")||k.contains("email"))return 20;
        if(k.contains("date")||k.contains("time")||k.contains("model")||k.contains("software")||k.contains("comment")||k.contains("description"))return 5;return 0;}
    static int score(List<Field> fs){return Math.min(100,fs.stream().filter(f->!f.value.isBlank()).mapToInt(Field::risk).sum());}
    static Document parse(byte[] data)throws Exception{
        var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");return f.newDocumentBuilder().parse(new ByteArrayInputStream(data));}
    static byte[] core(ZipFile z)throws IOException{ZipEntry e=z.getEntry(CORE);if(e==null)throw new IOException("No core properties part; this document cannot be edited by the built-in engine");try(var in=z.getInputStream(e)){byte[] b=in.readNBytes(1048577);if(b.length>1048576)throw new IOException("Core properties too large");return b;}}
    static List<Field> officeFields(Path p)throws Exception{try(ZipFile z=new ZipFile(p.toFile())){var d=parse(core(z));List<Field> out=new ArrayList<>();for(Node n=d.getDocumentElement().getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element){String k=n.getNodeName();out.add(new Field(k,n.getTextContent(),risk(k)));}return out;}}
    static void editOffice(Path src,Path dst,Map<String,String> changes)throws Exception{
        try(ZipFile z=new ZipFile(src.toFile())){var d=parse(core(z));Set<String> found=new HashSet<>();for(Node n=d.getDocumentElement().getFirstChild();n!=null;){Node next=n.getNextSibling();if(n instanceof Element && changes.containsKey(n.getNodeName())){String key=n.getNodeName(),v=changes.get(key);found.add(key);if(v.isEmpty())n.getParentNode().removeChild(n);else n.setTextContent(v);}n=next;}
            if(!found.equals(changes.keySet()))throw new IOException("Unknown core property");var tf=TransformerFactory.newInstance();tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");ByteArrayOutputStream xml=new ByteArrayOutputStream();tf.newTransformer().transform(new DOMSource(d),new StreamResult(xml));
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(dst,StandardOpenOption.CREATE_NEW))){var entries=z.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();out.putNextEntry(new ZipEntry(e.getName()));if(e.getName().equals(CORE))out.write(xml.toByteArray());else try(var in=z.getInputStream(e)){in.transferTo(out);}out.closeEntry();}}
        }
    }
    static String exif(List<String> args)throws Exception{
        List<String> command=new ArrayList<>();command.add(System.getenv().getOrDefault("EXIFTOOL","exiftool"));command.addAll(args);
        Path log=Files.createTempFile("metadata-",".txt");try{Process p;try{p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();}catch(IOException e){throw new IOException("ExifTool not found. Install it and add it to PATH or set EXIFTOOL.",e);}
            if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("ExifTool timeout");}if(Files.size(log)>8*1024*1024)throw new IOException("Metadata output exceeds 8 MiB");String s=Files.readString(log,StandardCharsets.UTF_8);if(p.exitValue()!=0)throw new IOException(s);return s;
        }finally{Files.deleteIfExists(log);}}
    static List<Field> inspect(Path p)throws Exception{
        if(OFFICE.contains(ext(p)))return officeFields(p);
        List<Field> out=new ArrayList<>();String raw=exif(List.of("-G1","-a","-s","-charset","UTF8","--",p.toAbsolutePath().toString()));
        for(String line:raw.split("\\R")){var m=java.util.regex.Pattern.compile("^\\[([^]]+)]\\s+(\\S+)\\s*:\\s*(.*)$").matcher(line);if(m.matches()&&!Set.of("System","File","ExifTool","Composite").contains(m.group(1))){String key=m.group(1)+":"+m.group(2);out.add(new Field(key,m.group(3),risk(key)));}}
        return out;
    }
    static void show(List<Field> fs){System.out.println("Exposure score: "+score(fs)+"/100 (heuristic, not a privacy guarantee)");for(int i=0;i<fs.size();i++){Field f=fs.get(i);System.out.printf("%3d | %2d | %s = %s%n",i+1,f.risk,f.key,f.value.replaceAll("[\\p{Cntrl}]","?"));}}
    static void export(Path src,Path dst,Map<String,String> changes)throws Exception{
        if(Files.exists(dst)||!ext(src).equals(ext(dst)))throw new IOException("Choose a new path with the same extension");
        if(OFFICE.contains(ext(src))){
            Path stage=Files.createTempDirectory(dst.toAbsolutePath().getParent(),"metadata-stage-");Path temp=stage.resolve(dst.getFileName());
            try{editOffice(src,temp,changes);List<Field> fields=inspect(temp);for(var e:changes.entrySet()){boolean present=fields.stream().anyMatch(f->f.key.equals(e.getKey())&&(e.getValue().isEmpty()||f.value.equals(e.getValue())));if(present==e.getValue().isEmpty())throw new IOException("Verification failed: "+e.getKey());}Files.move(temp,dst);}finally{Files.deleteIfExists(temp);Files.deleteIfExists(stage);}return;}
        if(!IMAGE.contains(ext(src)))throw new IOException("This format is inspection-only. PDF edits can leave recoverable metadata.");
        List<String> args=new ArrayList<>(List.of("-overwrite_original","-charset","UTF8"));for(var e:changes.entrySet()){if(!e.getKey().matches("[A-Za-z0-9_]+:[A-Za-z0-9_]+"))throw new IOException("Invalid tag");args.add("-"+e.getKey()+"="+e.getValue());}
        Files.copy(src,dst);args.add("--");args.add(dst.toAbsolutePath().toString());try{System.out.println(exif(args));}catch(Exception e){Files.deleteIfExists(dst);throw e;}
    }
    static Path path(String s){s=s.strip();if(s.startsWith("\"")&&s.endsWith("\""))s=s.substring(1,s.length()-1);return Path.of(s);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("--self-test")){Path d=Files.createTempDirectory("metadata-test"),a=d.resolve("a.docx"),b=d.resolve("b.docx");try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(a))){z.putNextEntry(new ZipEntry(CORE));z.write("<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:creator>Alice</dc:creator><dc:description>test</dc:description></cp:coreProperties>".getBytes(StandardCharsets.UTF_8));z.closeEntry();}export(a,b,Map.of("dc:creator","google.com","dc:description",""));if(!inspect(b).getFirst().value.equals("google.com")||inspect(b).size()!=1||!inspect(a).getFirst().value.equals("Alice"))throw new AssertionError();System.out.println("Metadata core-property tests passed");return;}
        Scanner in=new Scanner(System.in);System.out.print("File path: ");Path src=path(in.nextLine());if(!Files.isRegularFile(src))throw new IOException("Not a regular file");List<Field> fs=inspect(src);show(fs);Map<String,String> changes=new LinkedHashMap<>();
        while(true){System.out.print("Field number to edit/remove (Enter to export, q to quit): ");String s=in.nextLine().strip();if(s.equalsIgnoreCase("q"))return;if(s.isEmpty())break;int i=Integer.parseInt(s)-1;if(i<0||i>=fs.size()){System.out.println("Invalid field");continue;}System.out.print("New value (empty removes field): ");changes.put(fs.get(i).key,in.nextLine());}
        if(changes.isEmpty())return;System.out.print("New output file path: ");Path dst=path(in.nextLine());export(src,dst,changes);System.out.println("Re-inspection of output:");show(inspect(dst));System.out.println("Verify every requested change above. Other metadata may remain, including embedded documents, comments and revision history.");
    }
}
