package com.github.nekoos.legacyautoload.mapping.transformation;

import com.github.nekoos.legacyautoload.mapping.value.StructuredValue;
import com.github.nekoos.legacyautoload.mapping.value.StructuredValue.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A small explicit pipeline: case, structural separator, then controlled restrictions. */
public final class TransformationPipeline {
    private final CaseFormat caseFormat;
    private final Character separator;
    private final int length;
    private final boolean nonempty;

    TransformationPipeline(
            @NotNull CaseFormat caseFormat,
            Character separator,
            int length,
            boolean nonempty
    ) {
        this.caseFormat = caseFormat;
        this.separator = separator;
        this.length = length;
        this.nonempty = nonempty;
    }

    /** Inverts the declared stages in reverse order. */
    public @NotNull Optional<StructuredValue> reverse(@NotNull String rendered) {
        if ((length >= 0 && rendered.length() != length) || (nonempty && rendered.isEmpty())) {
            return Optional.empty();
        }
        List<String> renderedComponents = separator == null
                ? List.of(rendered)
                : List.of(rendered.split(java.util.regex.Pattern.quote(String.valueOf(separator)), -1));
        if (renderedComponents.stream().anyMatch(String::isEmpty)) {
            return Optional.empty();
        }
        List<Component> components = new ArrayList<>(renderedComponents.size());
        for (String component : renderedComponents) {
            Optional<Component> parsed = caseFormat.parse(component);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            components.add(parsed.get());
        }
        StructuredValue value = new StructuredValue(components);
        return apply(value).filter(rendered::equals).map(ignored -> value);
    }

    /** Applies the declared stages in order. */
    public @NotNull Optional<String> apply(@NotNull StructuredValue value) {
        List<String> rendered = value.components().stream().map(caseFormat::render).toList();
        String result = separator == null ? String.join("", rendered)
                : String.join(String.valueOf(separator), rendered);
        if ((length >= 0 && result.length() != length) || (nonempty && result.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    public int fixedWidth() {
        return length;
    }

    public boolean usesNamespaceSeparator() {
        return separator != null && separator == '\\';
    }
}
