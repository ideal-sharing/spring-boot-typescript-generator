package model.types;

public interface Type {
    default boolean needsValidation() {
        return false;
    }
}
