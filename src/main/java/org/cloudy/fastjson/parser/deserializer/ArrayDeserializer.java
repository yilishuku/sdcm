/*
 * Copyright 2015-2115 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @email   dragonmail2001@163.com
 * @author  jinglong.zhaijl
 * @date    2015-10-24
 *
 */
package org.cloudy.fastjson.parser.deserializer;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import org.cloudy.fastjson.JSONArray;
import org.cloudy.fastjson.parser.DefaultJSONParser;
import org.cloudy.fastjson.parser.JSONLexer;
import org.cloudy.fastjson.parser.JSONToken;
import org.cloudy.fastjson.util.TypeUtils;

public class ArrayDeserializer implements ObjectDeserializer {

    public final static ArrayDeserializer instance = new ArrayDeserializer();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        final JSONLexer lexer = parser.getLexer();
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }

        if (lexer.token() == JSONToken.LITERAL_STRING) {
            byte[] bytes = lexer.bytesValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) bytes;
        }

        Class componentClass;
        Type componentType;
        if (type instanceof GenericArrayType) {
            GenericArrayType clazz = (GenericArrayType) type;
            componentType = clazz.getGenericComponentType();
            if (componentType instanceof TypeVariable) {
                TypeVariable typeVar = (TypeVariable) componentType;
                Type objType = parser.getContext().getType();
                if (objType instanceof ParameterizedType) {
                    ParameterizedType objParamType = (ParameterizedType) objType;
                    Type objRawType = objParamType.getRawType();
                    Type actualType = null;
                    if (objRawType instanceof Class) {
                        TypeVariable[] objTypeParams = ((Class) objRawType).getTypeParameters();
                        for (int i = 0; i < objTypeParams.length; ++i) {
                            if (objTypeParams[i].getName().equals(typeVar.getName())) {
                                actualType = objParamType.getActualTypeArguments()[i];
                            }
                        }
                    }
                    if (actualType instanceof Class) {
                        componentClass = (Class) actualType;
                    } else {
                        componentClass = Object.class;
                    }
                } else {
                    componentClass = Object.class;
                }
            } else {
                componentClass = (Class) componentType;
            }
        } else {
            Class clazz = (Class) type;
            componentType = componentClass = clazz.getComponentType();
        }
        JSONArray array = new JSONArray();
        parser.parseArray(componentClass, array, fieldName);

        return (T) toObjectArray(parser, componentClass, array);
    }

    @SuppressWarnings("unchecked")
    private <T> T toObjectArray(DefaultJSONParser parser, Class<?> componentType, JSONArray array) {
        if (array == null) {
            return null;
        }

        int size = array.size();

        Object objArray = Array.newInstance(componentType, size);
        for (int i = 0; i < size; ++i) {
            Object value = array.get(i);

            if (value == array) {
                Array.set(objArray, i, objArray);
                continue;
            }

            if (componentType.isArray()) {
                Object element;
                if (componentType.isInstance(value)) {
                    element = value;
                } else {
                    element = toObjectArray(parser, componentType, (JSONArray) value);
                }

                Array.set(objArray, i, element);
            } else {
                Object element = null;
                if (value instanceof JSONArray) {
                    boolean contains = false;
                    JSONArray valueArray = (JSONArray) value;
                    int valueArraySize = valueArray.size();
                    for (int y = 0; y < valueArraySize; ++y) {
                        Object valueItem = valueArray.get(y);
                        if (valueItem == array) {
                            valueArray.set(i, objArray);
                            contains = true;
                        }
                    }
                    if (contains) {
                        element = valueArray.toArray();
                    }
                }

                if (element == null) {
                    element = TypeUtils.cast(value, componentType, parser.getConfig());
                }
                Array.set(objArray, i, element);

            }
        }

        array.setRelatedArray(objArray);
        array.setComponentType(componentType);
        return (T) objArray; // TODO
    }

    public int getFastMatchToken() {
        return JSONToken.LBRACKET;
    }
}
