package better.anticheat.core.check;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation allows checks to be easily marked with all required information without working with any redundant
 * constructors. This allows all important information for loading to be available upon creation and allows for it to be
 * easily seen by other developers.
 * Example Annotation:
 *
 * @CheckInfo( name = "Example",
 * category = "Combat",
 * config = "newconfig.conf",
 * experimental = true,
 * requirements = { ClientFeatureRequirement.CLIENT_TICK_END }
 * )
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CheckInfo {

    /**
     * This parameter should be the primary name of the command. This is what will be shown in alerts,
     */
    String name();

    /**
     * Return the category that this check belongs to.
     * Should typically be the folder of this check.
     */
    String category();

    /**
     * This refers to the configuration file that this check should be saved in and loaded from. It should include a
     * file extension (note that it will be saved and loaded as a hocon file regardless of the extension) and should not
     * include special characters or spacing characters. It does not need to be a file that already exists as
     * BetterAnticheat will generate it for you.
     * By default, the value is "checks.conf" to refer to the "checks.conf" file.
     */
    String config() default "checks.conf";

    /**
     * Return whether this check is experimental. If it is, it should be used with caution.
     */
    boolean experimental() default false;

    /**
     * Feature requirements this check depends on. If a player does not support a required feature,
     * the check will not be loaded for that player.
     */
    ClientFeatureRequirement[] requirements() default {};
}
