package better.anticheat.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation allows commands to be easily marked with all required information without working with any redundant
 * constructors. This allows all important information for loading to be available upon creation and allows for it to be
 * easily seen by other developers.
 * Example Annotation:
 * @CommandInfo(
 *     name = "example",
 *     config = "newconfig",
 *     parent = BACCommand.class,
 *     aliases = {"alias", "command", "includes-dash"}
 * )
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandInfo {

    /**
     * This parameter should be the primary name of the command. This is what it will be registered under in its
     * configuration file, what default permission nodes will generate around, and what the default command to run this
     * command object will be. It should not include spacing characters.
     */
    String name();

    /**
     * This refers to the configuration file that this command should be saved in and loaded from. It should include a
     * file extension (note that it will be saved and loaded as a hocon file regardless of the extension) and should not
     * include special characters or spacing characters. It does not need to be a file that already exists as
     * BetterAnticheat will generate it for you.
     * By default, the value is "commands.conf" to refer to the "commands.conf" file.
     */
    String config() default "commands.conf";

    /**
     * During load, the CheckManager will attempt to set the first object of this type that it can find as the parent of
     * the command. This is important as parent commands can determine enabled status for subcommands (if the parent is
     * disabled, all children will be disabled) and will require the parent command to be present while parsing (for
     * example, Alerts is a child of BAC. Alerts can only be run via /bac alerts or through other bac aliases).
     * If the parent cannot be resolved (using Command.class is the best case for this since it is abstract), the
     * command will be treated as a parent.
     */
    Class<? extends Command> parent() default Command.class;

    /**
     * This is an array of strings that will, by default, also result in this command object being processed. These can
     * be altered in the configuration for the command once it is generated.
     */
    String[] aliases() default {};
}
