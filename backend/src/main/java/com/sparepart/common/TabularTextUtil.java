package com.sparepart.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UTF-8 tab-separated text format used instead of encrypted office files. */
public final class TabularTextUtil {
    private static final String MAGIC = "#SPAREPART-TXT\t1";
    private TabularTextUtil() { }

    public record TextRow(int lineNumber, Map<String, String> values) {
        public String get(String name) { return values.get(name); }
    }

    public static void write(HttpServletResponse response, String filename, List<String> headers,
                             List<? extends List<?>> rows) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String encoded = URLEncoder.encode(filename + "_" + timestamp, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded + ".txt");
        var out = response.getOutputStream();
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        StringBuilder text = new StringBuilder(MAGIC).append('\n');
        appendRow(text, headers);
        for (List<?> row : rows) appendRow(text, row);
        out.write(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static List<TextRow> read(MultipartFile file, List<String> expectedHeaders, int maxRows) throws IOException {
        if (file == null || file.isEmpty()) throw new BizException("请选择TXT文本文件");
        if (file.getSize() > 10L * 1024 * 1024) throw new BizException("TXT文件不能超过10MB");
        String original = file.getOriginalFilename();
        if (original == null || !(original.toLowerCase().endsWith(".txt") || original.toLowerCase().endsWith(".tsv"))) {
            throw new BizException("只支持.txt或.tsv文本文件，请先下载系统模板");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        String[] lines = content.split("\\R", -1);
        int headerIndex = -1;
        List<String> actualHeaders = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.startsWith("#")) continue;
            actualHeaders = parseLine(line);
            headerIndex = i;
            break;
        }
        if (actualHeaders == null) throw new BizException("导入文件为空或缺少标题行");
        if (!actualHeaders.equals(expectedHeaders)) {
            throw new BizException("标题行不符合模板。请重新下载当前TXT模板，不要修改字段名称或顺序");
        }
        List<TextRow> result = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            if (lines[i].isBlank() || lines[i].startsWith("#")) continue;
            List<String> cells = parseLine(lines[i]);
            if (cells.size() != expectedHeaders.size()) {
                throw new BizException("第" + (i + 1) + "行字段数为" + cells.size() + "，应为" + expectedHeaders.size()
                        + "。请使用制表符分隔字段");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < expectedHeaders.size(); c++) values.put(expectedHeaders.get(c), cells.get(c));
            result.add(new TextRow(i + 1, values));
            if (result.size() > maxRows) throw new BizException("TXT数据不能超过" + maxRows + "行");
        }
        if (result.isEmpty()) throw new BizException("导入文件没有数据行");
        return result;
    }

    public static Integer integer(String value, int line, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException e) { throw new BizException("第" + line + "行“" + field + "”必须是整数，当前值：“" + value + "”"); }
    }

    public static Long longValue(String value, int line, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Long.valueOf(value.trim()); }
        catch (NumberFormatException e) { throw new BizException("第" + line + "行“" + field + "”必须是整数，当前值：“" + value + "”"); }
    }

    private static void appendRow(StringBuilder text, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) text.append('\t');
            text.append(escape(cells.get(i)));
        }
        text.append('\n');
    }

    private static List<String> parseLine(String line) {
        String[] cells = line.split("\\t", -1);
        List<String> values = new ArrayList<>(cells.length);
        for (String cell : cells) values.add(unescape(cell));
        return values;
    }

    private static String escape(Object value) {
        if (value == null) return "";
        return String.valueOf(value).replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                out.append(switch (ch) { case 't' -> '\t'; case 'n' -> '\n'; case 'r' -> '\r'; default -> ch; });
                escaped = false;
            } else if (ch == '\\') escaped = true;
            else out.append(ch);
        }
        if (escaped) out.append('\\');
        return out.toString();
    }
}
