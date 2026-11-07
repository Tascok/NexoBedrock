package dev.nexobedrock.conversion;

import dev.nexobedrock.pack.PackSource;

/**
 * Pedido de conversao. source nulo faz o servico escolher entre assets e zip
 * conforme a configuracao.
 */
public record ConversionRequest(
        String trigger,
        PackSource source,
        boolean force
) {
    public static ConversionRequest manual(boolean force) {
        return new ConversionRequest("command", null, force);
    }

    public static ConversionRequest event(String eventName, PackSource source) {
        return new ConversionRequest("event:" + eventName, source, false);
    }
}
