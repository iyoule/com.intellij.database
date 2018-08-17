import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

packageName = ""
typeMapping = [
        (~/(?i)bigint/)                   : "Long",
        (~/(?i)int/)                      : "Integer",
        (~/(?i)float|double|decimal|real/): "Double",
        (~/(?i)datetime|timestamp/)       : "java.time.LocalDateTime",
        (~/(?i)date/)                     : "java.time.LocalDate",
        (~/(?i)time/)                     : "java.time.LocalTime",
        (~/(?i)/)                         : "String"
]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable }.each { generate(it, dir) }
}

def generate(table, dir) {
    int index = dir.toString().lastIndexOf("/src/main/java/")
    if (index != -1) {
        packageName = dir.toString().substring(index + 15).replaceAll("/", ".")
    }
    def className = javaName(table.getName(), true)
    def fields = calcFields(table)
    def modelDir = dir.toString() + "/model/"
    def modelFile = new File(modelDir)
    modelFile.mkdir()
    new File(modelDir, className + ".java").withPrintWriter { out -> model(out, className, fields) }
}

def model(out, className, fields) {
    out.println "package ${packageName}.model;"
    out.println ""
    out.println "import io.swagger.annotations.ApiModel;"
    out.println "import io.swagger.annotations.ApiModelProperty;"
    out.println "import lombok.*;"
    out.println ""
    out.println "import java.io.Serializable;"
    out.println "import java.time.*;"
    out.println ""
    out.println "@Data"
    out.println "@Builder"
    out.println "@NoArgsConstructor"
    out.println "@AllArgsConstructor"
    out.println "@ApiModel(value = \"$className\")"
    out.println "public class $className implements Serializable {"
    out.println ""
    out.println "  public static final long serialVersionUID = 1L;"
    out.println ""
    fields.each() {
        if (it.commoent != "") {
            out.println " /**"
            out.println "  * ${it.comment}【${it.dataType}】"
            out.println "  */"
        }
        if (it.commoent != "") {
            out.println "  @ApiModelProperty(value = \"${it.comment}\", dataType = \"${it.dataType}\")"
        }
        if (it.annos != "") out.println "  ${it.annos}"
        out.println "  private ${it.type} ${it.name};"
        out.println ""
    }
    out.println ""
    out.println "}"
}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           left    : javaName(col.getName(), false),
                           right   : col.getName(),
                           name    : javaName(col.getName(), false),
                           dataType: col.getDataType(),
                           type    : typeStr,
                           comment : col.getComment(),
                           annos   : ""]]
    }
}

def javaName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}
