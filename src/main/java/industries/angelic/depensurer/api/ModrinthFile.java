package industries.angelic.depensurer.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A version on Modrinth, by project and version id or slug. Set both. */
@Retention(RetentionPolicy.CLASS)
@Target({})
public @interface ModrinthFile {

    String project() default "";

    String version() default "";
}
