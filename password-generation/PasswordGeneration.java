import java.security.SecureRandom;
import java.util.*;

/** Generates independently sampled Unicode code points from a deduplicated pool. */
public final class PasswordGeneration {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
        "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    private static final String GREEK = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψω";
    private static final String CYRILLIC = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЭЮЯабвгдежзийклмнопрстуфхцчшщэюя";
    private static final String HEBREW = "אבגדהוזחטיכלמנסעפצקרשת";
    private static final String ARABIC = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي";
    static int[] pool(String mode, String custom) {
        String chars = switch (mode.toLowerCase(Locale.ROOT)) {
            case "ascii" -> ASCII;
            case "mixed" -> ASCII + GREEK + CYRILLIC + HEBREW + ARABIC;
            case "random" -> ASCII + List.of(GREEK,CYRILLIC,HEBREW,ARABIC).get(RNG.nextInt(4));
            case "hebrew" -> ASCII + HEBREW;
            case "arabic" -> ASCII + ARABIC;
            case "greek" -> ASCII + GREEK;
            case "cyrillic" -> ASCII + CYRILLIC;
            case "custom" -> ASCII + custom;
            default -> throw new IllegalArgumentException("Unknown alphabet mode");
        };
        return chars.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isISOControl(c)
            && Character.isDefined(c) && Character.getType(c) != Character.SURROGATE && Character.getType(c) != Character.FORMAT && Character.getType(c) != Character.NON_SPACING_MARK && Character.getType(c) != Character.ENCLOSING_MARK && Character.getType(c) != Character.COMBINING_SPACING_MARK).distinct().toArray();
    }
    static String generate(int length, int[] pool) {
        if (length < 1 || length > 4096 || pool.length < 2) throw new IllegalArgumentException("Length: 1-4096; pool: at least two symbols");
        StringBuilder result = new StringBuilder();
        for (int i=0;i<length;i++) result.appendCodePoint(pool[RNG.nextInt(pool.length)]);
        return result.toString();
    }
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--self-test")) {
            int[] p=pool("mixed", ""); String s=generate(256,p);
            if(s.codePointCount(0,s.length())!=256 || pool("ascii", "").length!=94 || !s.codePoints().allMatch(c->Arrays.stream(p).anyMatch(x->x==c))) throw new AssertionError();
            System.out.println("Password tests passed"); return;
        }
        try {
            Scanner in = new Scanner(System.in);
            System.out.print("Length [32]: "); String input=in.nextLine().strip(); int length=input.isEmpty()?32:Integer.parseInt(input);
            System.out.print("Alphabet (ascii/mixed/random/greek/cyrillic/hebrew/arabic/custom) [ascii]: "); String mode=in.nextLine().strip(); if(mode.isEmpty())mode="ascii";
            String custom=""; if(mode.equalsIgnoreCase("custom")){System.out.print("Additional characters: ");custom=in.nextLine();}
            int[] p=pool(mode,custom); String secret=generate(length,p);
            System.out.printf(Locale.ROOT,"Pool: %d | Theoretical entropy: %.1f bits%n",p.length,length*Math.log(p.length)/Math.log(2));
            System.out.println(secret);
            System.out.println("No file or network storage. Unicode support depends on the target service; terminal output remains visible.");
        } catch (Exception e) { System.err.println("Error: "+e.getMessage()); System.exit(1); }
    }
}
