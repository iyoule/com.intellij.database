import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil
import org.apache.commons.lang3.StringUtils

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

packageName = ""
basePackageName = ""
gmtCreate = ["gmt_create"] as String[]
gmtModified = ["gmt_modified"] as String[]
isDeleteProperties = ["is_delete"] as String[]
typeMapping = [
        (~/(?i)bigint/)                   : "Long",
        (~/(?i)int/)                      : "Integer",
        (~/(?i)float|double|decimal|real/): "Double",
        (~/(?i)datetime|timestamp/)       : "java.time.LocalDateTime",
        (~/(?i)date/)                     : "java.time.LocalDate",
        (~/(?i)time/)                     : "java.time.LocalTime",
        (~/(?i)/)                         : "String"
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
    def fields = calcFields(table)
    def tableName = table.getName()
    def xmlDir = dir.toString().substring(0, index + 10) + sepa + "resources" + sepa + "mapper" + sepa
    def baseXmlDir = dir.toString().substring(0, index + 10) + sepa + "resources" + sepa + "mapper" + sepa + "base" + sepa
    def baseXmlFile = new File(baseXmlDir)
    baseXmlFile.mkdirs()
    new File(baseXmlDir, className + "BaseMapper.xml").withPrintWriter { out -> baseXml(out, tableName, className, fields) }
    def xmlFile = new File(xmlDir, className + "Mapper.xml")
    if (!xmlFile.exists()) {
        xmlFile.withPrintWriter { out -> xml(out, tableName, className, fields) }
    }
}

def baseXml(out, tableName, className, fields) {
    int index = 0
    out.println "<?xml version='1.0' encoding='UTF-8' ?>"
    out.println "<!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' 'http://mybatis.org/dtd/mybatis-3-mapper.dtd' >"
    out.println "<mapper namespace='${packageName}.mapper.base.${className}BaseMapper'>"
    out.println ""
    out.println "    <sql id='Base_Column_List'>"
    out.print "    "
    fields.each() {
        if (index != 0) {
            out.print ", "
        }
        out.print "${tableName}.${it.right}"
        index++
    }
    out.println ""
    out.println "    </sql>"
    out.println ""
    out.println "    <select id='selectByPrimaryKey' resultType='${packageName}.model.${className}Model'"
    out.println "            parameterType='java.util.Map'>"
    out.println "        select "
    out.println "        <include refid='Base_Column_List'/>"
    out.println "        from ${tableName} "
    out.println "        where ${tableName}.id = #{id}"
    out.println "    </select>"
    fields.each() {
        String str = it.right
        if (str.endsWith("_id")) {
            def ForeignKey = javaName(it.right, true)
            def foreignKey = javaName(it.right, false)
            out.println ""
            out.println "    <select id='selectBy${ForeignKey}' resultType='${packageName}.model.${className}Model'"
            out.println "            parameterType='java.lang.Long'>"
            out.println "        select "
            out.println "        <include refid='Base_Column_List'/>"
            out.println "        from ${tableName} "
            out.println "        where ${tableName}.${it.right} = #{${foreignKey}}"
            out.println "    </select>"
        }
    }
    out.println ""
    out.println "    <select id='getSelectBoxByQuery' resultType='${basePackageName}.core.common.SelectBox'"
    out.println "            parameterType='java.util.Map'>"
    out.println "        select ${tableName}.id as label, ${tableName}.id as value from ${tableName}"
    out.println "        <where>"
    out.println "            <include refid='query_filter'/>"
    out.println "        </where>"
    out.println "    </select>"
    out.println ""
    out.println "    <select id='selectOneByQuery' resultType='${packageName}.model.${className}Model'"
    out.println "            parameterType='java.util.Map'>"
    out.println "        select "
    out.println "        <include refid='Base_Column_List'/>"
    out.println "        from ${tableName} "
    out.println "        <where>"
    out.println "            <include refid='query_filter'/>"
    out.println "        </where>"
    out.println "        Limit 1"
    out.println "    </select>"
    out.println ""
    out.println "    <select id='selectByQuery' resultType='${packageName}.model.${className}Model'"
    out.println "            parameterType='java.util.Map'>"
    out.println "        select "
    out.println "        <include refid='Base_Column_List'/>"
    out.println "        from ${tableName} "
    out.println "        <where>"
    out.println "            <include refid='query_filter'/>"
    out.println "        </where>"
    out.println "    </select>"
    out.println ""
    if (propertiesContainField(isDeleteProperties, fields)) {
        out.println "    <delete id='deleteByPrimaryKey' parameterType='java.lang.Long'>"
        out.println "        delete from ${tableName} where ${tableName}.id = #{id}"
        out.println "    </delete>"
        out.println ""
    } else {
        out.println "    <update id='deleteByPrimaryKey' parameterType='java.lang.Long'>"
        out.println "        update ${tableName} set ${tableName}.${isDeleteProperties[0]} = 1 where ${tableName}.id = #{id}"
        out.println "    </update>"
        out.println ""
    }
    fields.each() {
        String str = it.right
        if (str.endsWith("_id")) {
            def ForeignKey = javaName(it.right, true)
            def foreignKey = javaName(it.right, false)
            if (propertiesContainField(isDeleteProperties, fields)) {
                out.println "    <delete id='deleteByPrimaryKey' parameterType='java.lang.Long'>"
                out.println "        delete from ${tableName} where ${tableName}.${it.right} = #{${foreignKey}}"
                out.println "    </delete>"
                out.println ""
            } else {
                out.println "    <update id='deleteBy${ForeignKey}' parameterType='java.lang.Long'>"
                out.println "        update ${tableName} set ${tableName}.${isDeleteProperties[0]} = 1 where ${tableName}.${it.right} = #{${foreignKey}}"
                out.println "    </update>"
                out.println ""
            }
        }
    }
    if (propertiesContainField(isDeleteProperties, fields)) {
        out.println "    <select id='deleteByQuery' parameterType='java.util.Map'>"
        out.println "        delete from ${tableName}"
        out.println "        <where>"
        out.println "            <include refid='query_filter'/>"
        out.println "        </where>"
        out.println "    </delete>"
        out.println ""
    } else {
        out.println "    <update id='deleteByQuery' parameterType='java.util.Map'>"
        out.println "        update ${tableName} set ${tableName}.${isDeleteProperties[0]} = 1"
        out.println "        <where>"
        out.println "            <include refid='query_filter'/>"
        out.println "        </where>"
        out.println "    </update>"
        out.println ""
    }
    out.println "    <select id='count' resultType='java.lang.Integer' parameterType='java.util.Map'>"
    out.println "        select count(*) from ${tableName}"
    out.println "        <where>"
    out.println "            <include refid='query_filter'/>"
    out.println "        </where>"
    out.println "    </select>"
    out.println ""
    out.println "    <insert id='insert' parameterType='java.util.Map' useGeneratedKeys='true' keyProperty='id'>"
    out.println "        insert into ${tableName}"
    out.println "        <trim prefix='(' suffix=')' suffixOverrides=','>"
    fields.each() {
        if (propertiesContainField(it.right, gmtCreate)) {
            out.println "            ${tableName}.${it.right},"
        } else if (propertiesContainField(it.right, gmtModified)) {
            out.println "            ${tableName}.${it.right},"
        } else if (propertiesContainField(it.right, isDeleteProperties)) {
            out.println "            ${tableName}.${it.right},"
        } else {
            out.println "            <if test='${it.left} != null'>${tableName}.${it.right},</if>"
        }
    }
    out.println "        </trim>"
    out.println "        <trim prefix='values (' suffix=')' suffixOverrides=','>"
    fields.each() {
        if (propertiesContainField(it.right, gmtCreate)) {
            out.println "            now(),"
        } else if (propertiesContainField(it.right, gmtModified)) {
            out.println "            now(),"
        } else if (propertiesContainField(it.right, isDeleteProperties)) {
            out.println "            0,"
        } else {
            out.println "            <if test='${it.left} != null'>#{${it.left}},</if>"
        }
    }
    out.println "        </trim>"
    out.println "    </insert>"
    out.println ""
    out.println "    <update id='update' parameterType='java.util.Map'>"
    out.println "        update ${tableName}"
    out.println "        <set>"
    fields.each() {
        if (propertiesContainField(it.right, gmtCreate)) {
        } else if (propertiesContainField(it.right, gmtModified)) {
            out.println "            ${tableName}.${it.right} = now(),"
        } else {
            out.println "            <if test='${it.left} != null'>${tableName}.${it.right} = #{${it.left}},</if>"
        }
    }
    out.println "        </set>"
    out.println "        where ${tableName}.id = #{id}"
    out.println "    </update>"
    out.println ""
    out.println "    <sql id='query_filter'>"
    fields.each() {
        if (propertiesContainField(it.right, isDeleteProperties)) {
            out.println "        and ${tableName}.${it.right} != 1"
        } else {
            out.println "        <if test='${it.left} != null'>and ${tableName}.${it.right} = #{${it.left}}</if>"
        }
    }
    out.println "    </sql>"
    out.println ""
    out.println "</mapper>"
}

def xml(out, tableName, className, fields) {
    out.println "<?xml version='1.0' encoding='UTF-8' ?>"
    out.println "<!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' 'http://mybatis.org/dtd/mybatis-3-mapper.dtd' >"
    out.println "<mapper namespace='${packageName}.mapper.${className}Mapper'>"
    out.println ""
    out.println "</mapper>"
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

def tableName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}
