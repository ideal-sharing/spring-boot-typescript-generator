package model.types;

import lombok.Data;
import lombok.NonNull;

@Data
public class Field {
    @NonNull
    private String name;
    private boolean required = true;
    @NonNull
    private Type type;
}
