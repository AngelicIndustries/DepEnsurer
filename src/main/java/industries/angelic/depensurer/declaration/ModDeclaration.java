package industries.angelic.depensurer.declaration;

import java.util.Collections;
import java.util.List;

/** One {@code @Mod} class and the deps it declares, as read from a {@code META-INF/depensurer.json}. */
public final class ModDeclaration {

    public final String modId;
    public final String className;
    /** Where the declaration came from, for log messages: a jar path or a class path URL. */
    public final String source;
    public final List<DepDeclaration> deps;

    public ModDeclaration(String modId, String className, String source, List<DepDeclaration> deps) {
        this.modId = modId;
        this.className = className;
        this.source = source;
        this.deps = Collections.unmodifiableList(deps);
    }

    public DepDeclaration dep(String id) {
        for (DepDeclaration dep : deps) {
            if (dep.id.equals(id)) {
                return dep;
            }
        }
        return null;
    }
}
