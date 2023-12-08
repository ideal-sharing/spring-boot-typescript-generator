package model.types;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
public class ObjectType implements NamedType {
    @NonNull
    private String name;
    @ToString.Exclude
    private List<Field> fields = new ArrayList<>();

    private boolean needsValidation;

    @Override
    public boolean needsValidation() {
        return needsValidation;
    }
}
