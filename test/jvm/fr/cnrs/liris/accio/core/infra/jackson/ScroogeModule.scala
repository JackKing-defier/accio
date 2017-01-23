/*
 * Accio is a program whose purpose is to study location privacy.
 * Copyright (C) 2016-2017 Vincent Primault <vincent.primault@liris.cnrs.fr>
 *
 * Accio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Accio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Accio.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnrs.liris.accio.core.infra.jackson

import com.fasterxml.jackson.databind.module.SimpleModule

object ScroogeModule extends SimpleModule {
  addDeserializer(classOf[BigStruct], new ScroogeStructDeserializer(BigStruct))
  addDeserializer(classOf[NestedStruct], new ScroogeStructDeserializer(NestedStruct))
  addDeserializer(classOf[StructWithStrings], new ScroogeStructDeserializer[StructWithStrings](StructWithStrings))
  addDeserializer(classOf[StructWithBooleans], new ScroogeStructDeserializer[StructWithBooleans](StructWithBooleans))
  addDeserializer(classOf[StructWithBytes], new ScroogeStructDeserializer[StructWithBytes](StructWithBytes))
  addDeserializer(classOf[StructWithShorts], new ScroogeStructDeserializer[StructWithShorts](StructWithShorts))
  addDeserializer(classOf[StructWithInts], new ScroogeStructDeserializer[StructWithInts](StructWithInts))
  addDeserializer(classOf[StructWithLongs], new ScroogeStructDeserializer[StructWithLongs](StructWithLongs))
  addDeserializer(classOf[StructWithDoubles], new ScroogeStructDeserializer[StructWithDoubles](StructWithDoubles))
  addDeserializer(classOf[StructWithStructs], new ScroogeStructDeserializer[StructWithStructs](StructWithStructs))
  addDeserializer(classOf[StructWithLists], new ScroogeStructDeserializer[StructWithLists](StructWithLists))
  addDeserializer(classOf[StructWithSets], new ScroogeStructDeserializer[StructWithSets](StructWithSets))
  addDeserializer(classOf[StructWithMaps], new ScroogeStructDeserializer[StructWithMaps](StructWithMaps))
  addDeserializer(classOf[TestEnum], new ScroogeEnumDeserializer[TestEnum](TestEnum))
  addDeserializer(classOf[TestUnion], new ScroogeUnionDeserializer[TestUnion](TestUnion))

  addSerializer(new ScroogeStructSerializer)
  addSerializer(new ScroogeEnumSerializer)
}