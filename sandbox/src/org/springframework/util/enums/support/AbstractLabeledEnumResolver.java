/*
 * Copyright 2002-2004 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.util.enums.support;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.util.CachingMapTemplate;
import org.springframework.util.enums.LabeledEnum;
import org.springframework.util.enums.LabeledEnumResolver;

/**
 * Abstract base class for labeled enum resolvers.
 * @author Keith Donald
 */
public abstract class AbstractLabeledEnumResolver implements LabeledEnumResolver {
	protected transient final Log logger = LogFactory.getLog(getClass());

	private CachingMapTemplate labeledEnumCache = new CachingMapTemplate() {
		protected Object create(Object key) {
			Map typeEnums = findLabeledEnums((String)key);
			if (typeEnums != null) {
				return typeEnums;
			}
			else {
				return Collections.EMPTY_MAP;
			}
		}
	};

	protected AbstractLabeledEnumResolver() {
	}

	public Collection getLabeledEnumCollection(String type) {
		return Collections.unmodifiableSet(new TreeSet(getLabeledEnumMap(type).values()));
	}

	public Map getLabeledEnumMap(String type) {
		Assert.notNull(type, "No type specified");
		Map typeEnums = (Map)labeledEnumCache.get(type);
		return Collections.unmodifiableMap(typeEnums);
	}

	public LabeledEnum getLabeledEnum(String type, Comparable code) {
		Assert.notNull(code, "No enum code specified");
		Map typeEnums = getLabeledEnumMap(type);
		LabeledEnum codedEnum = (LabeledEnum)typeEnums.get(code);
		if (codedEnum == null) {
			logger.info("No enum found of type '" + type + "' with '" + code.getClass() + " code " + code
					+ "', returning null.");
		}
		return codedEnum;
	}

	public LabeledEnum getLabeledEnum(String type, String label) {
		Map typeEnums = getLabeledEnumMap(type);
		Iterator it = typeEnums.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry)it.next();
			LabeledEnum value = (LabeledEnum)entry.getValue();
			if (value.getLabel().equals(label)) {
				return value;
			}
		}
		return null;
	}

	public LabeledEnum getRequiredEnum(String type, Comparable code) throws IllegalStateException {
		LabeledEnum codedEnum = getLabeledEnum(type, code);
		if (codedEnum == null) {
			throw new IllegalStateException("Enum does not exist with type '" + type + "', code " + code);
		}
		return codedEnum;
	}

	protected Map findLabeledEnums(String type) {
		logger.info("Assuming no enums exist for type " + type + "'");
		return null;
	}
}