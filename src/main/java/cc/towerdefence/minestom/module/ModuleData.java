package cc.towerdefence.minestom.module;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleData {

    @NotNull String name();

    // If the module fails, and it is required, the server will not accept connections.
    boolean required();

    Class<? extends Module>[] dependencies() default {};

}
