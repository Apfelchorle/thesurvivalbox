package org.thesandbox.core.commands;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class CommandMessages
{
    public CommandMessages()
    {
    }

    public static String command(String message)
    {
        return apply("&7&lCommand &8» &7", message, false);
    }

    public static String error(String message)
    {
        return apply("&c&lError &8» &c", message, false);
    }

    public static String usage(String message)
    {
        return apply("&c&lUsage &8» &c", message, true);
    }

    public static String server(String message)
    {
        return apply("&c&lServer &8» &c", message, false);
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
        String coloredPrefix = LegacyComponentSerializer.legacyAmpersand().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(prefix)
        );

        return coloredPrefix + body;
    }

    public static String stripLeadingColor(String input)
    {
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

            if (value.length() >= 2 && (value.charAt(0) == '§' || value.charAt(0) == '&'))
            {
                char code = Character.toLowerCase(value.charAt(1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r')
                {
                    value = value.substring(2);
                    changed = true;
                }
            }
        }

        return value;
    }

    /// checks if given value is hex
    ///
    /// @return Boolean
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isHex(char c)
    {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
