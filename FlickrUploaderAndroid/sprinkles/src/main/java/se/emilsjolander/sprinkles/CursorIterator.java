package se.emilsjolander.sprinkles;

import android.database.Cursor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

class CursorIterator<T extends Model> implements Iterator<T> {

	private Cursor cursor;
	private Class<T> type;
	private int pos = -1;
	private int count;

	CursorIterator(Cursor cursor, Class<T> type) {
		this.cursor = cursor;
		this.type = type;
		this.count = cursor == null ? 0 : cursor.getCount();
	}

	@Override
	public boolean hasNext() {
		return (pos + 1) < count;
	}

	List<String> colNames;

	@Override
	public T next() {
		pos++;
		cursor.moveToPosition(pos);
		if (colNames == null)
			colNames = Arrays.asList(cursor.getColumnNames());
		return Utils.getResultFromCursor(type, cursor, colNames);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}
