package me.dego.estudio.shop;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

    // Aplica una bextura base64 (basehead-XXXXXX)

public class SkullTextureApplier {

    private static final Logger LOGGER = Bukkit.getLogger();

    public static void apply(SkullMeta meta, String base64Texture) {
        try {
            String decodedJson = new String(Base64.getDecoder().decode(base64Texture));
            String skinUrl = extractSkinUrl(decodedJson);
            if (skinUrl == null) {
                LOGGER.warning("[Shop] No se pudo extraer la URL de textura del base64 proporcionado.");
                return;
            }

            // 1. Bukkit.createProfile(UUID, String) — buscamos el método por firma de parámetros,
            //    sin importar qué tipo devuelva en este build concreto.
            Method createProfileMethod = findMethod(Bukkit.class, "createProfile", UUID.class, String.class);
            if (createProfileMethod == null) {
                LOGGER.warning("[Shop] Este build no tiene Bukkit.createProfile(UUID,String). No se puede aplicar textura custom.");
                return;
            }
            Object profile = createProfileMethod.invoke(null, UUID.randomUUID(), "CustomHead");

            // 2. profile.getTextures() — el objeto de texturas mutable
            Method getTexturesMethod = findMethod(profile.getClass(), "getTextures");
            if (getTexturesMethod == null) {
                LOGGER.warning("[Shop] El perfil de este build no tiene getTextures().");
                return;
            }
            Object textures = getTexturesMethod.invoke(profile);

            // 3. textures.setSkin(URL) — normalmente esto ya muta el estado interno del profile
            Method setSkinMethod = findMethod(textures.getClass(), "setSkin", URL.class);
            if (setSkinMethod == null) {
                LOGGER.warning("[Shop] El objeto de texturas de este build no tiene setSkin(URL).");
                return;
            }
            setSkinMethod.invoke(textures, new URL(skinUrl));

            // 4. profile.setTextures(textures) — algunos builds lo requieren explícitamente,
            //    otros no (setSkin ya mutó el estado). Si no existe el método, no pasa nada.
            Method setTexturesMethod = findMethod(profile.getClass(), "setTextures", getTexturesMethod.getReturnType());
            if (setTexturesMethod != null) {
                setTexturesMethod.invoke(profile, textures);
            }

            // 5. meta.setOwnerProfile(profile) — buscamos CUALQUIER método llamado así en
            //    la clase real de meta, sea cual sea el tipo de parámetro que declare.
            Method setOwnerProfileMethod = findMethodByNameOnly(meta.getClass(), "setOwnerProfile");
            if (setOwnerProfileMethod == null) {
                LOGGER.warning("[Shop] SkullMeta de este build no tiene setOwnerProfile(...).");
                return;
            }
            setOwnerProfileMethod.invoke(meta, profile);

        } catch (Exception e) {
            LOGGER.warning("[Shop] No se pudo aplicar la textura de cabeza custom: " + e
                    + " (esto no rompe el resto del menú, la cabeza se mostrará sin textura personalizada)");
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Busca un método solo por nombre, ignorando el tipo exacto de parámetro (para setOwnerProfile). */
    private static Method findMethodByNameOnly(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static String extractSkinUrl(String decodedJson) {
        int urlIndex = decodedJson.indexOf("\"url\":\"");
        if (urlIndex == -1) return null;
        int start = urlIndex + "\"url\":\"".length();
        int end = decodedJson.indexOf('"', start);
        if (end == -1) return null;
        return decodedJson.substring(start, end);
    }
}
