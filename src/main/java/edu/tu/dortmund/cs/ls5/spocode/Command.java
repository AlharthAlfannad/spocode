package edu.tu.dortmund.cs.ls5.spocode;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public class Command {
  public static String[] accessModifiers = {"static", "public", "private", "protected", "no"};
  public static String[] keys = {"return", "rename", "create", "open", "add", "call",
          "remove", "go", "delete", "undo", "redo", "declare", "initialize", "format", "push", "assign"};
  public static String[] simpleKeys = {"undo", "redo", "format"};
  public static String[] targets = {"class", "attribute", "file", "method", "interface", "package", "line", "variable", "getter", "setter", "with the message", "if",
          "else", "while"};
  public static String[] primitiveTypes = {"int", "integer", "char", "bite", "byte", "long", "double", "float", "void"};
  public static ArrayList<String> typeNames = new ArrayList<>();


  private String key;
  private String target;
  private String accessModifier = "";
  private String type = "";
  private ArrayList<String> parameters = new ArrayList<>();
  public Command(String command) {
    String[] elements = command.trim().split("\\s+");
    for (int element = 0; element < elements.length; element++) {
      elements[element] = StringUtils.uncapitalize(elements[element]);
      if (elements[element].equals("integer")) elements[element] = "int";
      if (elements[element].equals("bite")) elements[element] = "byte";
    }
    boolean searchForAccessModifier = false;
    boolean searchForTarget = true;
    int keyIndex = 0;
    // search for key
    for (; keyIndex < elements.length; keyIndex++) {
      if (isKey(elements[keyIndex])) {
        key = elements[keyIndex];
        if (key.equals("go") && keyIndex + 1 < elements.length && elements[keyIndex + 1].equals("to")) {
          keyIndex++;
        }
        if (isSimpleCommand(key)) {
          target = "simple";
          return;
        }
        if (key.equals("declare") || key.equals("initialize") || key.equals("assign") || key.equals("return")) {
          if (keyIndex + 1 < elements.length && !elements[keyIndex + 1].equals("variable")) {
            target = "variable";
            searchForTarget = false;
          }
        }
        if (key.equals("call") && keyIndex + 1 < elements.length && !elements[keyIndex + 1].equals("method")) {
          target = "method";
          searchForTarget = false;
        }
        if (key.equals("push") && keyIndex + 3 < elements.length && elements[keyIndex + 1].equals("with") &&
                elements[keyIndex + 2].equals("the") && elements[keyIndex + 3].equals("message")) {
          target = "with the message";
          keyIndex+=3;
          searchForTarget = false;
        }
        break;
      }
    }
    int targetIndex = keyIndex + 1;
    if (searchForTarget) {
      for (; targetIndex < elements.length; targetIndex++) {
        if (isTarget(elements[targetIndex])) {
          target = elements[targetIndex];
          if (key.equals("add")) {
            if (target.equals("method") || target.equals("attribute")) {
              searchForAccessModifier = true;
            }
          }

          if (target.equals("else") && targetIndex + 1 < elements.length && elements[targetIndex + 1].equals("if")) {
            target = "else if";
            targetIndex++;
          }
          break;
        }
      }
    } else {
      targetIndex--;
    }
    int accessModifierIndex;
    int parametersIndex = targetIndex + 1;
    if (searchForAccessModifier) {
      accessModifierIndex = targetIndex + 1;
      for (; accessModifierIndex < elements.length; accessModifierIndex++) {
        if (isAccessModifier(elements[accessModifierIndex])) {
          accessModifier = elements[accessModifierIndex];
          if (elements[accessModifierIndex].equals("no") && accessModifierIndex + 1 < elements.length
                  && elements[accessModifierIndex + 1].equals("modifier")) {
            accessModifierIndex++;
          }
          if (accessModifierIndex + 1 < elements.length
                  && elements[accessModifierIndex].equals("static")){
            accessModifier += " static";
          }
          break;
        }
      }
      if (accessModifierIndex < elements.length) {
        parametersIndex = accessModifierIndex + 1;
      }
    }
    for (; parametersIndex < elements.length; parametersIndex++) {
      parameters.add(elements[parametersIndex]);
    }

    if (!isCommand()) {
      parameters.clear();
      key = null;
      target = null;
    }
  }

  private boolean isSimpleCommand(String word) {
    for (String simpleKey : simpleKeys) {
      if (word.equals(simpleKey)) {
        return true;
      }
    }
    return false;
  }


  public boolean isCommand() {
    return key != null && target != null;
  }

  public void addParameter(String parameter) {
    parameters.add(parameter);
  }

  public ArrayList<String> getParameters() {
    return parameters;
  }

  public String getKey() {
    return key;
  }

  public String getTarget() {
    return target;
  }

  public String getType() {
    return type;
  }

  public void show() {
    System.out.print("Key: " + key + "\n" + "Target: " + target + "\n" + "AccessModifier: " + accessModifier +
            "\n" + "Type: " + type + "\nParameters: ");
    for (String parameter : parameters) {
      System.out.print(parameter + ", ");
    }
    System.out.println();
  }

  public boolean isKey(String word) {
    for (String key : keys) {
      if (key.equals(word)) return true;
    }
    return false;
  }

  public boolean isTarget(String word) {
    for (String target : targets) {
      if (target.equals(word)) return true;
    }
    return false;
  }

  public boolean isAccessModifier(String word) {
    for (String modifier : accessModifiers) {
      if (modifier.equals(word)) return true;
    }
    return false;
  }

  public boolean isTypeName(String word) {
    for (String typeName : typeNames) {
      if (typeName.equals(word)) {
        return true;
      }
    }
    return false;
  }


  boolean isPrimitiveType(String word) {
    for (String type : primitiveTypes) {
      if (type.equals(word)) return true;
    }
    return false;
  }

  public String getAccessModifier() {
    return accessModifier;
  }

  public boolean hasAccessModifier() {
    return !accessModifier.equals("");
  }

  public boolean hasType() {
    return !type.equals("");
  }

  public boolean hasParameters() {
    return !parameters.isEmpty();
  }
}

