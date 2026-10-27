package test.java.lab2;

import lab2.MiniStress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Этап 7: Тесты Store Buffering и аппаратной модели памяти (MiniStress)")
public class Stage7Test {

    @Test
    @DisplayName("Режим volatile (seq_cst): запрещенный исход (0, 0) строго отсутствует (0 раз)")
    void testVolatileStrictNoStoreBuffering() throws Exception {
        String output = runMiniStress("volatile", 200, 10000);
        assertTrue(output.contains("mode=volatile"), "Вывод должен содержать mode=volatile");

        // В режиме volatile число исходов (0,0) обязано быть строго 0
        long forbiddenCount = parseForbiddenCount(output);
        assertEquals(0L, forbiddenCount, "В режиме volatile исход (0,0) строго запрещен моделью памяти seq_cst!");
    }

    @Test
    @DisplayName("Режим acqrel: аппаратный Store Buffering проявляется на реальном процессоре (исход (0, 0) > 0)")
    void testAcqRelStoreBufferingAppears() throws Exception {
        long forbiddenCount = 0;
        for (int attempt = 0; attempt < 3 && forbiddenCount == 0; attempt++) {
            String output = runMiniStress("acqrel", 1000, 10000);
            assertTrue(output.contains("mode=acqrel"), "Вывод должен содержать mode=acqrel");
            forbiddenCount = parseForbiddenCount(output);
        }
        assertTrue(forbiddenCount > 0,
                "На реальном процессоре с буферами записи (Store Buffers) исход (0,0) должен возникнуть хотя бы раз!");
    }

    @Test
    @DisplayName("Режим plain: без барьеров памяти исход (0, 0) также физически возникает на железе")
    void testPlainStoreBufferingAppears() throws Exception {
        long forbiddenCount = 0;
        for (int attempt = 0; attempt < 3 && forbiddenCount == 0; attempt++) {
            String output = runMiniStress("plain", 1000, 10000);
            assertTrue(output.contains("mode=plain"));
            forbiddenCount = parseForbiddenCount(output);
        }
        assertTrue(forbiddenCount > 0, "В режиме plain исход (0,0) должен физически наблюдаться на x86!");
    }

    private String runMiniStress(String mode, int rounds, int cells) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(baos));
            MiniStress.main(new String[]{mode, String.valueOf(rounds), String.valueOf(cells)});
        } finally {
            System.setOut(oldOut);
        }
        return baos.toString();
    }

    private long parseForbiddenCount(String output) {
        // Ищем строку с "(0,0)" и парсим число
        for (String line : output.split("\\R")) {
            if (line.contains("(0,0)")) {
                String[] parts = line.trim().split("\\s+");
                // parts[0] = "(0,0)", parts[1] = число с запятыми или без
                String numStr = parts[1].replace(",", "").replace(".", "").replace("\u00a0", "").replace("\u202f", "");
                return Long.parseLong(numStr);
            }
        }
        throw new IllegalStateException("Не найдена строка с (0,0) в выводе: " + output);
    }
}
