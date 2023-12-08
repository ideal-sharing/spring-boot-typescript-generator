package model.types;

import lombok.Data;
import lombok.NonNull;

@Data
public class ArrayType implements Type {
    @NonNull
    private Type subType;
}
