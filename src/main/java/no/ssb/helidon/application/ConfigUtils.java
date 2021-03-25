package no.ssb.helidon.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.helidon.config.Config;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigUtils {

    static public final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    static public List<Config.Key> keys(Config.Key key) {
        LinkedList<Config.Key> keys = new LinkedList<>();
        Config.Key next = key;
        while (next != null && !next.isRoot()) {
            keys.addFirst(next);
            next = next.parent();
        }
        return keys;
    }

    static public String configAsYaml(Config config) {
        try {
            return yamlMapper.writeValueAsString(configAsJackson(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static public ObjectNode configAsJackson(Config config) {
        ObjectNode root = yamlMapper.createObjectNode();
        config.traverse()
                .filter(c -> !c.key().isRoot())
                .forEach(c -> {
                    List<Config.Key> keys = keys(c.key());
                    String fqk = keys.stream().map(Config.Key::name).collect(Collectors.joining("."));
                    JsonNode node = root;
                    for (int i = 0; i < keys.size() - 1; i++) {
                        if (node.isObject()) {
                            node = node.get(keys.get(i).name());
                        } else if (node.isArray()) {
                            node = node.get(Integer.parseInt(keys.get(i).name()));
                        } else {
                            throw new RuntimeException("Node is not object nor array");
                        }
                    }
                    if (node == null) {
                        return;
                    }
                    if (node.isArray()) {
                        switch (c.type()) {
                            case OBJECT:
                                node = ((ArrayNode) node).addObject();
                                break;
                            case LIST:
                                node = ((ArrayNode) node).addArray();
                                break;
                            case VALUE:
                                node = ((ArrayNode) node).add(c.asString().get());
                                break;
                            case MISSING:
                                break;
                        }
                    } else if (node.isObject()) {
                        switch (c.type()) {
                            case OBJECT:
                                node = ((ObjectNode) node).putObject(c.key().name());
                                break;
                            case LIST:
                                node = ((ObjectNode) node).putArray(c.key().name());
                                break;
                            case VALUE:
                                node = ((ObjectNode) node).put(c.key().name(), c.asString().get());
                                break;
                            case MISSING:
                                break;
                        }
                    } else {
                        throw new RuntimeException("Not object or array");
                    }
                });
        return root;
    }
}
