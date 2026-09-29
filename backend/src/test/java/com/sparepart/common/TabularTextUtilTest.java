package com.sparepart.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TabularTextUtilTest {
    @Test
    void readsUtf8BomAndEscapedText() throws Exception {
        String content = "\uFEFF#SPAREPART-TXT\t1\n编码\t备注\nA-01\t第一行\\n第二行\n";
        MockMultipartFile file = new MockMultipartFile("file", "data.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        List<TabularTextUtil.TextRow> rows = TabularTextUtil.read(file, List.of("编码", "备注"), 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("备注")).isEqualTo("第一行\n第二行");
        assertThat(rows.get(0).lineNumber()).isEqualTo(3);
    }

    @Test
    void rejectsOfficeFile() {
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", "application/octet-stream", new byte[]{1});
        BizException error = assertThrows(BizException.class,
                () -> TabularTextUtil.read(file, List.of("编码"), 10));
        assertThat(error.getMessage()).contains("只支持.txt或.tsv");
    }
}
