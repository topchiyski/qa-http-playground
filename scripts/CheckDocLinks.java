import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds broken links in the repository's Markdown.
 *
 * <p>A link that leads nowhere costs a reader more than no link at all. This check follows every relative link and
 * every {@code #anchor}, and exits with code 1 if any of them is broken. Web links are skipped: they need the
 * network, and this check should not.
 *
 * <p>Run it from the repository root. Java 21 runs a single source file directly:
 * {@code java scripts/CheckDocLinks.java}
 */
public class CheckDocLinks {

    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)");
    private static final Pattern NOT_SLUG = Pattern.compile("[^\\w\\- ]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SCHEME = Pattern.compile("^[a-z]+:.*");
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", "target", "node_modules");

    private static final Map<Path, Set<String>> ANCHORS = new HashMap<>();

    /**
     * Checks every link, prints the broken ones, and exits with 1 if there are any.
     */
    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        List<String> bad = new ArrayList<>();
        for (Path file : markdownFiles(root)) {
            Matcher m = LINK.matcher(Files.readString(file));
            while (m.find()) {
                String link = m.group(1);
                if (SCHEME.matcher(link).matches()) {
                    continue;   // http:, https:, mailto: ...
                }
                int hash = link.indexOf('#');
                String target = hash < 0 ? link : link.substring(0, hash);
                String fragment = hash < 0 ? "" : link.substring(hash + 1);
                Path resolved = target.isEmpty() ? file : file.getParent().resolve(target).normalize();
                String where = root.relativize(file).toString().replace('\\', '/') + " -> " + link;
                if (!Files.exists(resolved)) {
                    bad.add(where + " (missing file)");
                } else if (!fragment.isEmpty() && resolved.toString().endsWith(".md")
                        && !anchors(resolved).contains(fragment)) {
                    bad.add(where + " (missing anchor)");
                }
            }
        }
        if (bad.isEmpty()) {
            System.out.println("all links ok");
        } else {
            bad.forEach(System.out::println);
            System.exit(1);
        }
    }

    /**
     * Every Markdown file under {@code root}, in a stable order. Build output and Git internals are skipped.
     */
    private static List<Path> markdownFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> {
                        for (Path part : root.relativize(p)) {
                            if (SKIPPED_DIRS.contains(part.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
    }

    /**
     * The anchors a Markdown file offers, computed the way GitHub computes them.
     *
     * <p>Lower case, punctuation removed, spaces turned into hyphens. Results are cached, because popular files are
     * linked many times.
     */
    private static Set<String> anchors(Path file) {
        return ANCHORS.computeIfAbsent(file, f -> {
            try (Stream<String> lines = Files.lines(f)) {
                return lines.map(HEADING::matcher)
                        .filter(Matcher::matches)
                        .map(h -> NOT_SLUG.matcher(h.group(1).strip().toLowerCase()).replaceAll("").replace(' ', '-'))
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                return Set.of();
            }
        });
    }
}
