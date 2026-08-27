package com.github.nekoos.autoloadmapping.settings;

import com.github.nekoos.autoloadmapping.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.MappingState;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.SchemeState;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned, framework-independent interchange format for complete mapping schemes. */
public final class AutoloadMappingSchemeCodec {
    public static final String FILE_EXTENSION = "autoload-mapping.xml";
    private static final String ROOT = "autoload-mapping-scheme";
    private static final String VERSION = "1";

    public @NotNull String encode(@NotNull SchemeState scheme) {
        try {
            Document document = newDocumentBuilderFactory().newDocumentBuilder().newDocument();
            Element root = document.createElement(ROOT);
            root.setAttribute("version", VERSION);
            root.setAttribute("name", scheme.name);
            document.appendChild(root);

            for (MappingState mapping : scheme.mappings) {
                Element rule = document.createElement("rule");
                set(rule, "name", mapping.name);
                set(rule, "base-directory", mapping.baseDirectory);
                set(rule, "class-pattern", mapping.classPattern);
                set(rule, "file-pattern", mapping.filePattern);
                set(rule, "fallback-chain", mapping.fallbackChainId);
                set(rule, "fallback-order", Integer.toString(mapping.fallbackOrder));
                set(rule, "quick-fix-policy", mapping.quickFixPolicy);
                root.appendChild(rule);
            }

            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter result = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(result));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not export the mapping scheme", exception);
        }
    }

    public @NotNull SchemeState decode(@NotNull String xml) {
        try {
            Document document = newDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (!ROOT.equals(root.getTagName()) || !VERSION.equals(root.getAttribute("version"))) {
                throw new IllegalArgumentException("Unsupported autoload mapping scheme format");
            }
            String name = required(root, "name");
            List<MappingState> mappings = new ArrayList<>();
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element rule) || !"rule".equals(rule.getTagName())) {
                    continue;
                }
                int fallbackOrder = Integer.parseInt(required(rule, "fallback-order"));
                MappingState mapping = new MappingState(
                        UUID.randomUUID().toString(),
                        required(rule, "name"),
                        required(rule, "base-directory"),
                        required(rule, "class-pattern"),
                        required(rule, "file-pattern"),
                        required(rule, "fallback-chain"),
                        fallbackOrder,
                        policy(required(rule, "quick-fix-policy"))
                );
                // Reject a malformed imported rule instead of silently dropping it during normalization.
                mapping.toMapping();
                mappings.add(mapping);
            }
            SchemeState imported = new SchemeState(UUID.randomUUID().toString(), name, mappings);
            imported.mappings = new ArrayList<>(AutoloadMappingProjectSettings.normalizeMappings(imported.mappings));
            return imported;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid autoload mapping scheme", exception);
        }
    }

    private static @NotNull DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static void set(@NotNull Element element, @NotNull String name, @NotNull String value) {
        element.setAttribute(name, value == null ? "" : value);
    }

    private static @NotNull String required(@NotNull Element element, @NotNull String attribute) {
        if (!element.hasAttribute(attribute)) {
            throw new IllegalArgumentException("Missing scheme attribute: " + attribute);
        }
        return element.getAttribute(attribute);
    }

    private static @NotNull String policy(@NotNull String value) {
        try {
            return AmbiguousQuickFixPolicy.valueOf(value).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown quick-fix policy: " + value, exception);
        }
    }
}
