package model.validation;

import lombok.NonNull;

public class Arrays {
    public record MinLength(int minLength, @NonNull String message) implements Validation { }
    public record MaxLength(int maxLength, @NonNull String message) implements Validation { }
}
