package industries.angelic.depensurer.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container for repeated {@link EnsureDep}s. Write the {@code @EnsureDep}s directly instead. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface EnsureDeps {

    EnsureDep[] value();
}
