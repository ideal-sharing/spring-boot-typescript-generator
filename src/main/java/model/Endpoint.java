package model;

import lombok.Data;
import lombok.NonNull;
import model.types.Field;
import model.types.Type;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
public class Endpoint {
     @NonNull
     private String className;
     @NonNull
     private String name;
     @NonNull
     private String url;
     @NonNull
     private HttpMethod httpMethod;
     private List<Field> urlArgs = new ArrayList<>();
     private Type body = null;
     private List<Field> params = new ArrayList<>();


     public List<Field> getAllVariables() {
          List<Field> params = new ArrayList<>();
          params.addAll(getUrlArgs());
          params.addAll(getParams());
          return params.stream().sorted(Comparator.comparing(Field::isRequired, Boolean::compareTo).reversed().thenComparing(Field::getName)).toList();
     }

     @NonNull
     private Type returnType;
}
