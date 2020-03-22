/*
 * Copyright (c) 2019. Kin-Hong Wong. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==============================================================================
 */

package com.easymobo.openlabeler.model;

import org.w3c.dom.Element;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.QName;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ModelUtil
{
   public static class BooleanAdapter extends XmlAdapter<Integer, Boolean>
   {
      @Override
      public Boolean unmarshal(Integer s) {
         return s == null ? null : s == 1;
      }

      @Override
      public Integer marshal(Boolean c) {
         return c == null ? null : c ? 1 : 0;
      }
   }

   // Top left starts at (1, 1)
   public static class OneBasedPointAdapter extends XmlAdapter<Integer, Double>
   {
      @Override
      public Double unmarshal(Integer s) {
         return s.doubleValue() - 1d;
      }

      @Override
      public Integer marshal(Double c) {
         return (int) Math.round(c) + 1;
      }
   }

   public static class PointAdapter extends XmlAdapter<Integer, Double>
   {
      @Override
      public Double unmarshal(Integer s) {
         return s.doubleValue();
      }

      @Override
      public Integer marshal(Double c) {
         return (int) Math.round(c);
      }
   }

   public static class OneBasedPointListAdapter extends XmlAdapter<PointListWrapper, List<Double>>
   {
      @Override
      public List<Double> unmarshal(PointListWrapper wrapper) {
         return wrapper.toList();
      }

      @Override
      public PointListWrapper marshal(List<Double> values) {
         if (values == null || values.size() <= 0) {
            return null;
         }
         return new PointListWrapper(values);
      }
   }

   @XmlType
   // Coordinates are 1-based
   public static class PointListWrapper
   {
      private List<JAXBElement<Integer>> list = new ArrayList<>();

      public PointListWrapper() {}

      @XmlAnyElement
      public List<JAXBElement<Integer>> getElements() {
         return list;
      }

      public PointListWrapper(List<Double> values) {
         IntStream.range(0, values.size()).forEach(idx -> {
            var name = idx % 2 == 0 ? "x" + ((idx / 2) + 1) : "y" + ((idx / 2) + 1);
            list.add(new JAXBElement<>(new QName(name), Integer.class, (int) Math.round(values.get(idx)) + 1));
         });
      }

      public List<Double> toList() {
         //Due to type erasure, we cannot use elements.stream() directly when unmashalling
         List<?> elements = list;
          List<Double> r = elements.stream().sorted((lhs, rhs) -> {
            var lhsName = extractLocalName(lhs);
            var rhsName = extractLocalName(rhs);
            // Compare x1 vs x11 or y1 vs y11
            int result = Integer.valueOf(lhsName.substring(1)).compareTo(Integer.valueOf(rhsName.substring(1)));
            if (result == 0) {
                // Compare x vs y
                result = lhsName.substring(0, 1).compareTo(rhsName.substring(0, 1));
            }
            return result;
         }).map(e -> {
            var value = extractTextContent(e);
            return Double.valueOf(value) - 1d;
         }).collect(Collectors.toList());
         return r;
      }

      /**
       * <p>
       * Extract local name from <code>obj</code>, whether it's javax.xml.bind.JAXBElement or org.w3c.dom.Element;
       * </p>
       *
       * @param obj
       * @return
       */
      @SuppressWarnings("unchecked")
      private static String extractLocalName(Object obj) {
         Map<Class<?>, Function<? super Object, String>> strFuncs = new HashMap<>();
         strFuncs.put(JAXBElement.class, (jaxb) -> ((JAXBElement<String>) jaxb).getName().getLocalPart());
         strFuncs.put(Element.class, ele -> ((Element) ele).getLocalName());
         return extractPart(obj, strFuncs).orElse("");
      }

      /**
       * <p>
       * Extract text content from <code>obj</code>, whether it's javax.xml.bind.JAXBElement or org.w3c.dom.Element;
       * </p>
       *
       * @param obj
       * @return
       */
      @SuppressWarnings("unchecked")
      private static String extractTextContent(Object obj) {
         Map<Class<?>, Function<? super Object, String>> strFuncs = new HashMap<>();
         strFuncs.put(JAXBElement.class, (jaxb) -> ((JAXBElement<String>) jaxb).getValue());
         strFuncs.put(Element.class, ele -> ((Element) ele).getTextContent());
         return extractPart(obj, strFuncs).orElse("");
      }

      /**
       * Check class type of <code>obj</code> according to types listed in <code>strFuncs</code> keys,
       * then extract some string part from it according to the extract function specified in <code>strFuncs</code>
       * values.
       *
       * @param obj
       * @param strFuncs
       * @return
       */
      private static <ObjType, T> Optional<T> extractPart(ObjType obj, Map<Class<?>, Function<? super ObjType, T>> strFuncs) {
         for (Class<?> clazz : strFuncs.keySet()) {
            if (clazz.isInstance(obj)) {
               return Optional.of(strFuncs.get(clazz).apply(obj));
            }
         }
         return Optional.empty();
      }
   }
}
