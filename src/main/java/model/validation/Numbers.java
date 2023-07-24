package model.validation;

import lombok.NonNull;

public class Numbers {
    public record MinValue(long minValue, @NonNull String message) implements Validation { }
    public record MaxValue(long maxValue, @NonNull String message) implements Validation { }
}
