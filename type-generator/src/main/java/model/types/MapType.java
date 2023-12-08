package model.types;

import lombok.Data;
import lombok.NonNull;

@Data
public class MapType implements Type {
    @NonNull
    private Type keySubType;
    @NonNull
    private Type valueSubType;
}
