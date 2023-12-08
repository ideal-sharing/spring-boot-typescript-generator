package model.validation;

import lombok.NonNull;

public class Strings {
    public record Email(@NonNull String message) implements Validation { }
    public record Regex(@NonNull String regex, @NonNull String message) implements Validation { }
}
