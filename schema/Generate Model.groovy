import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

packageName = ""
basePackageName = ""
commonProperties = ["id", "gmt_create", "gmt_modified", "is_delete", "operater", "operater_id"] as String[]
typeMapping = [
        (~/(?i)bigint/)                   : "Long",
        (~/(?i)int/)                      : "Integer",
        (~/(?i)float|double|decimal|real/): "Double",
        (~/(?i)datetime|timestamp/)       : "java.time.LocalDateTime",
        (~/(?i)date/)                     : "java.time.LocalDate",
        (~/(?i)time/)                     : "java.time.LocalTime",
        (~/(?i)/)                         : "String"
]

dataTypeMapping = [
        (~/(?i)bigint/)                   : "integer",
        (~/(?i)int/)                      : "integer",
        (~/(?i)float|double|decimal|real/): "number",
        (~/(?i)datetime|timestamp/)       : "string",
        (~/(?i)date/)                     : "string",
        (~/(?i)time/)                     : "string",
        (~/(?i)/)                         : "string"
]

exampleMapping = [
        (~/(?i)bigint/)                   : "1",
        (~/(?i)int/)                      : "1",
        (~/(?i)float|double|decimal|real/): "1.00",
        (~/(?i)datetime|timestamp/)       : LocalDateTime.now(),
        (~/(?i)date/)                     : LocalDate.now(),
        (~/(?i)time/)                     : LocalTime.now(),
        (~/(?i)/)                         : "占位符"
]

sepa = java.io.File.separator

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable }.each { generate(it, dir) }
}

def generate(table, dir) {
    int index = dir.toString().lastIndexOf(sepa + "src" + sepa + "main" + sepa + "java" + sepa)
    if (index != -1) {
        packageName = dir.toString().substring(index + 15).replaceAll(sepa, ".")
    }
    index_last = packageName.lastIndexOf(".")
    if (index_last != -1) {
        basePackageName = packageName.toString().substring(0, index_last)
    }
    def className = javaName(table.getName(), true)
    def tableComment = table.getComment()
    def fields = calcFields(table)
    def modelDir = dir.toString() + sepa + "model" + sepa
    def modelFile = new File(modelDir)
    modelFile.mkdirs()
    new File(modelDir, className + "Model.java").withPrintWriter { out -> model(out, className, tableComment, fields) }
}

def model(out, className, tableComment, fields) {
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
    out.println "@ApiModel(value = \"${className}Model\", description = \"${tableComment}\")"
    out.println "public class ${className}Model implements Serializable {"
    out.println ""
    out.println "  public static final long serialVersionUID = 1L;"
    out.println ""
    fields.each() {
        if (propertiesContainField(it.right, commonProperties)) {
            if (it.commoent != "") {
                out.println " /**"
                out.println "  * ${it.comment}【${it.colDataType}】"
                out.println "  */"
            }
            if (it.commoent != "") {
                out.println "  @ApiModelProperty(value = \"${it.comment}【${it.colDataType}】\", dataType = \"${it.dataType}\", example = \"${it.example}\", hidden = true)"
            }
            if (it.annos != "") out.println "  ${it.annos}"
            out.println "  private ${it.type} ${it.name};"
            out.println ""
        } else {
            if (it.commoent != "") {
                out.println " /**"
                out.println "  * ${it.comment}【${it.colDataType}】"
                out.println "  */"
            }
            if (it.commoent != "") {
                out.println "  @ApiModelProperty(value = \"${it.comment}【${it.colDataType}】\", dataType = \"${it.dataType}\", example = \"${it.example}\")"
            }
            if (it.annos != "") out.println "  ${it.annos}"
            out.println "  private ${it.type} ${it.name};"
            out.println ""
        }
    }
    out.println ""
    out.println "}"
}

boolean fieldsContainPropertie(propertie, fields) {
    def isExsit = false
    fields.each() {
        if (propertie == it.right) {
            isExsit = true
        }
    }
    isExsit
}

boolean propertiesContainField(field, properties) {
    def isExsit = false
    properties.each() {
        if (field == it) {
            isExsit = true
        }
    }
    isExsit
}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        def dataType = dataTypeMapping.find { p, t -> p.matcher(spec).find() }.value
        def example = exampleMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           left       : javaName(col.getName(), false),
                           right      : col.getName(),
                           name       : javaName(col.getName(), false),
                           dataType   : dataType,
                           colDataType: col.getDataType(),
                           example    : example,
                           type       : typeStr,
                           comment    : col.getComment(),
                           annos      : ""]]
    }
}

def javaName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}

