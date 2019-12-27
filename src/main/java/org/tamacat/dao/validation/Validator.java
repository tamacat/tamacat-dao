package org.tamacat.dao.validation;

import org.tamacat.dao.meta.Column;

public interface Validator {

	Validator validate(Column column, Object value);
}
