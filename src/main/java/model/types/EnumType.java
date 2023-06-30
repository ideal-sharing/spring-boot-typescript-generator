package model.types;

import lombok.Data;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

@Data

public class EnumType implements NamedType {
    @NonNull
    private String name;
    private List<String> values = new ArrayList<>();
}
