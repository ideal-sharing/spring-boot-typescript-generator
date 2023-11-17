# Installation

Install the maven plugin with maven:
```
mvn clean install
```

# Usage

After installation ad the Plugin to your `pom.xml`:

```xml
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>ch.ideal</groupId>
                    <artifactId>type-generator</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <inherited>false</inherited>
                    <configuration>
                        <outputDir>YourApp/src/api</outputDir>
                        <backend>Spring</backend>
                        <frontendAPI>ReactQuery</frontendAPI>
                        <frontendTypes>Zod</frontendTypes>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
```

To generate the typescript files in your `outputDir`, compile your project and run the plugin with:
```
mvn clean compile
mvn -N type-generator:type-generator
```
It's best to automate this process during development such that it is executed during startup of the spring application.

# Configuration

## outputDir
Configures the directory in which the generated typescript files are written. Multiple directories seperated by commas are permitted.
The following hierarchy is created inside each of the output directories:
```
/types       // All used type definitions are generated here (parameters & return types of endpoints)
/endpoints   // The API to the endpoints is generated here
```

## subModules
Defines what maven submodules(if any) should be scanned for classes, if empty, no submodules are considered for scanning.
Multiple values can be seperated with a comma, e.g `submodule1, submodule2, submodule3`

## useStringAsDate
A boolean that specifies whether Typescript `Date` or `string` (for durther use with e.g `dayjs`) should be used for storing Date like objects.

## Framework Configs

| Option        | Description                                                    | Possible Values         | 
|---------------|----------------------------------------------------------------|-------------------------|
| backend       | Which backend should be used                                   | spring                  |
| frontendAPI   | For which frontend framework should the interface be generated | `Angular`, `ReactQuery` |
| frontendTypes | What type model should be used for the Frontend                | `Typescript`, `Zod`     |

