import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class after {
    public static void main(String[] args) throws Exception {
        final String baseDir = args == null ? "." : args[0];

        Path path = Paths.get(baseDir, "reference", "en", "en-US", "modules", "RESTEasy_Spring_Integration.xml");
        Charset charset = StandardCharsets.UTF_8;
        String content = new String(Files.readAllBytes(path), charset);
        String token = "_SPRING_VER_";
        String ver = getVer(baseDir);

        // restore
        content = content.replaceAll(ver, token);
        Files.write(path, content.getBytes(charset));
    }

    public static String getVer(final String dir) throws Exception {
        Path path = Paths.get(dir, "..", "resteasy-dependencies-bom", "pom.xml");
        Charset charset = StandardCharsets.UTF_8;

        String content = new String(Files.readAllBytes(path), charset);

        Pattern pattern = Pattern.compile("<version\\.org\\.springframework>(.*)</version\\.org\\.springframework>");
        Matcher matcher = pattern.matcher(content);
        matcher.find();
        return matcher.group(1);
    }
}
