package frontend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import model.TypeContext;
import model.types.ArrayType;
import model.types.MapType;
import model.types.NamedType;
import model.types.Type;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Data
public class TypeScriptFile {

    private String location;
    private List<Import> imports = new ArrayList<>();
    private String body;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Import {
        private String location;
        private String defaultImport;
        private Set<String> imports;

        public String toString() {
            StringBuilder imp = new StringBuilder("import ");

            if(defaultImport != null) {
                imp.append(defaultImport);
            }

            if(imports != null && imports.size() > 0) {
                if(defaultImport != null) {
                    imp.append(", ");
                }
                imp.append("{ ").append(String.join(", ", imports)).append(" }");
            }

            imp.append(" from '").append(location).append("';\n");
            return imp.toString();
        }
    }

    @SneakyThrows
    public void write() {
        File f = new File(location + ".ts");
        f.getParentFile().mkdirs();
        f.delete();
        f.createNewFile();

        try(FileWriter fileWriter = new FileWriter(location + ".ts")) {
            BufferedWriter writer = new BufferedWriter(fileWriter);

            for (Import imp : imports) {
                writer.write(imp.toString());
            }
            if(imports.size() > 0) {
                writer.write("\n");
            }

            writer.write(body);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getImportLocationFor(TypeScriptFile other) {
        Path first = Paths.get(this.location);
        Path second = Paths.get(other.location);

        String location = first.getParent().relativize(second).toString();

        if(!location.contains("/")) {
            location = "./" + location;
        }

        return location;
    }

    public void addImport(Type t, TypeContext context) {
        if(t instanceof NamedType o) {
            this.addImport(o, context);
        }

        if(t instanceof ArrayType arr ) {
            addImport(arr.getSubType(), context);
        }

        if(t instanceof MapType m) {
            addImport(m.getKeySubType(), context);
            addImport(m.getValueSubType(), context);
        }
    }

    public void addImport(NamedType o, TypeContext context) {
        TypeScriptFile toImport = context.getNamedObjectFiles().get(o.getName());

        if(toImport != this) {
            String location = this.getImportLocationFor(toImport);

            Optional<Import> importOptional = this.getImports()
                    .stream()
                    .filter(imp -> imp.getLocation().equals(location))
                    .findFirst();

            if (importOptional.isPresent()) {
                if (importOptional.get().getDefaultImport() == null) {
                    importOptional.get().setDefaultImport(o.getName());
                } else if (!importOptional.get().getDefaultImport().equals(o.getName())) {
                    throw new RuntimeException("Wrong default import present in type expected " + o.getName() + " was " + importOptional.get().getDefaultImport());
                }
            } else {
                TypeScriptFile.Import newImport = new TypeScriptFile.Import();
                newImport.setDefaultImport(o.getName());
                newImport.setLocation(location);
                this.getImports().add(newImport);
            }
        }
    }
}
