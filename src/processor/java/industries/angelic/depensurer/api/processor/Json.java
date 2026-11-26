package industries.angelic.depensurer.api.processor;

import java.util.List;
import java.util.Map;

/** Writes the maps, lists, strings, numbers and booleans the processor builds. Processors can't count on Gson. */
final class Json {

    private Json() {}

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n')
            .toString();
    }

    private static void write(StringBuilder out, Object value, int depth) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                indent(out, depth + 1);
                string(out, String.valueOf(entry.getKey()));
                out.append(": ");
                write(out, entry.getValue(), depth + 1);
                out.append(++n < map.size() ? ",\n" : "\n");
            }
            indent(out, depth);
            out.append('}');
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(out, depth + 1);
                write(out, list.get(i), depth + 1);
                out.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            indent(out, depth);
            out.append(']');
        } else if (value instanceof String) {
            string(out, (String) value);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else {
            throw new IllegalArgumentException("Can't write " + value);
        }
    }

    private static void indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
