package org.thesandbox.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.thesandbox.core.util.GenericDataKeys;

public final class CommandMessages
{
    private CommandMessages()
    {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String command(String message)
    {
        return apply(GenericDataKeys.CMD_MSGS_COMMAND, message, false);
    }

    public static String error(String message)
    {
        return apply(GenericDataKeys.CMD_MSGS_ERROR, message, false);
    }

    public static String usage(String message)
    {
        return apply(GenericDataKeys.CMD_MSGS_USAGE, message, true);
    }

    public static String server(String message)
    {
        return apply(GenericDataKeys.CMD_MSGS_SERVER, message, false);
    }

    public static String information(String message) {
        return apply(GenericDataKeys.CMD_MSGS_INFO, message, false);
    }

    public static String apply(String prefix, String message, boolean usage)
    {
        String body = message == null ? "" : stripLeadingColor(message);
        if (usage)
        {
            if (body.regionMatches(true, 0, "Usage: ", 0, 7))
            {
                body = body.substring(7);
            }
            else if (body.regionMatches(true, 0, "Usage:", 0, 6))
            {
                body = body.substring(6).stripLeading();
            }
        }

        // Serializes to section-formatted String (§c) while supporting legacy hex (&#ffffff)
        Component comp = LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + body);
        return LegacyComponentSerializer.legacySection().serialize(comp);
    }

    public static String stripLeadingColor(String input)
    {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String value = input;
        boolean changed = true;

        while (changed && !value.isEmpty())
        {
            changed = false;

            if (value.length() >= 14 && (value.charAt(0) == '§' || value.charAt(0) == '&')
                    && Character.toLowerCase(value.charAt(1)) == 'x')
            {
                char marker = value.charAt(0);
                boolean valid = true;
                for (int i = 2; i < 14; i += 2)
                {
                    if (value.charAt(i) != marker || !isHex(value.charAt(i + 1)))
                    {
                        valid = false;
                        break;
                    }
                }
                if (valid)
                {
                    value = value.substring(14);
                    changed = true;
                    continue;
                }
            }

            // Standard Hex (&#ffffff or §#ffffff)
            if (value.length() >= 8 && (value.startsWith("&#") || value.startsWith("§#")))
            {
                boolean valid = true;
                for (int i = 2; i < 8; i++)
                {
                    if (!isHex(value.charAt(i)))
                    {
                        valid = false;
                        break;
                    }
                }
                if (valid)
                {
                    value = value.substring(8);
                    changed = true;
                    continue;
                }
            }

            // Standard legacy color/formatting codes (&0-&f, &k-&o, &r)
            if (value.length() >= 2 && (value.charAt(0) == '§' || value.charAt(0) == '&'))
            {
                char code = Character.toLowerCase(value.charAt(1));
                if (isFormattingOrColor(code))
                {
                    value = value.substring(2);
                    changed = true;
                }
            }
        }

        return value;
    }

    private static boolean isFormattingOrColor(char code) {
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')
                || (code >= 'k' && code <= 'o');
    }

    public static boolean isHex(char c)
    {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}