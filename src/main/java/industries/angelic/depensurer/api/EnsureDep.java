package industries.angelic.depensurer.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a jar whose assets the annotated {@code @Mod} class uses. Dep Ensurer finds the jar in the mods folder (the
 * full build also downloads it), keeps it from loading as a mod and serves the included assets. Needs the annotation
 * processor from the api jar, which writes {@code META-INF/depensurer.json}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Repeatable(EnsureDeps.class)
public @interface EnsureDep {

    /** Handle for this dep, unique within the declaring mod. */
    String id();

    /** Shown to players on the missing files screen. */
    String name() default "";

    /** Expected file name. Needed for the CurseForge CDN and shown on the missing files screen. */
    String file() default "";

    /** SHA-1 of the accepted files, lowercase hex. Downloads must match one of these. */
    String[] sha1() default {};

    /**
     * Loose match for jars the player or a launcher installed: the mod id in mcmod.info, mods.toml, neoforge.mods.toml
     * or fabric.mod.json.
     */
    String modId() default "";

    /** Maven version range for the loose match, for example {@code [1.6,2.0)}. Needs {@link #modId()}. */
    String versions() default "";

    /** Id of another dep of this mod that this one can stand in for. The first declared dep is preferred. */
    String alternativeOf() default "";

    CurseForgeFile curseforge() default @CurseForgeFile;

    ModrinthFile modrinth() default @ModrinthFile;

    /** Extra download mirrors, used by the full build only. */
    String[] urls() default {};

    /** Globs over jar entries that get served. {@code *} stays within a path segment, {@code **} crosses them. */
    String[] include() default { "assets/**" };

    String[] exclude() default {};

    /** Prefix rules {@code "from -> to"}, for example {@code "assets/aether/ -> assets/aetherii/"}. */
    String[] remap() default {};

    /** Rename the jar to .jar.disabled so it never loads. False leaves it loading and reads assets from it in place. */
    boolean neutralize() default true;

    Side side() default Side.CLIENT;

    /** Missing is not an error; check {@code DepEnsurerAPI.isPresent}. */
    boolean optional() default false;

    enum Side {
        /** Assets only: a dedicated server neither downloads nor reports it, but still neutralizes it. */
        CLIENT,
        /** Also needed on the server, for example for structures or data files. */
        BOTH
    }
}
