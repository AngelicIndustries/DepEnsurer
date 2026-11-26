package industries.angelic.depensurer.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A file on CurseForge. Set the project and file ids together; the slug gives the file page link. */
@Retention(RetentionPolicy.CLASS)
@Target({})
public @interface CurseForgeFile {

    int project() default 0;

    String slug() default "";

    int file() default 0;
}
