package dev.emortal.minestom.core.module;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleData {

    @NotNull String name();

    // If the module fails, and it is required, the server will not accept connections.
    boolean required();

    /**
     * The soft dependencies of this module.
     * There should be no hard dependencies as almost every module should half-function without the other modules.
     *
     * @return Classes of the soft dependencies
     */
    Class<? extends Module>[] softDependencies() default {};

}
