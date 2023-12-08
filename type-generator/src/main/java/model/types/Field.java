package model.types;

import lombok.Data;
import lombok.NonNull;
import model.validation.Validation;

import java.util.ArrayList;
import java.util.List;

@Data
public class Field {
    @NonNull
    private String name;
    private boolean required = true;
    @NonNull
    private Type type;

    private List<Validation> validations = new ArrayList<>();
}
