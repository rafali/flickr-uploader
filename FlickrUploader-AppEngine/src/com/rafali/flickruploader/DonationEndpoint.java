package com.rafali.flickruploader;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.datanucleus.query.JDOCursorHelper;

@Api(name = "donationendpoint", namespace = @ApiNamespace(ownerDomain = "rafali.com", ownerName = "rafali.com", packagePath = "flickruploader"))
public class DonationEndpoint {

	/**
	 * This method lists all the entities inserted in datastore.
	 * It uses HTTP GET method and paging support.
	 *
	 * @return A CollectionResponse class containing the list of all entities
	 * persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listDonation")
	public CollectionResponse<Donation> listDonation(@Nullable @Named("cursor") String cursorString, @Nullable @Named("limit") Integer limit) {

		PersistenceManager mgr = null;
		Cursor cursor = null;
		List<Donation> execute = null;

		try {
			mgr = getPersistenceManager();
			Query query = mgr.newQuery(Donation.class);
			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				HashMap<String, Object> extensionMap = new HashMap<String, Object>();
				extensionMap.put(JDOCursorHelper.CURSOR_EXTENSION, cursor);
				query.setExtensions(extensionMap);
			}

			if (limit != null) {
				query.setRange(0, limit);
			}

			execute = (List<Donation>) query.execute();
			cursor = JDOCursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (Donation obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<Donation> builder().setItems(execute).setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 *
	 * @param id the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getDonation")
	public Donation getDonation(@Named("id") Long id) {
		PersistenceManager mgr = getPersistenceManager();
		Donation donation = null;
		try {
			donation = mgr.getObjectById(Donation.class, id);
		} finally {
			mgr.close();
		}
		return donation;
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already
	 * exists in the datastore, an exception is thrown.
	 * It uses HTTP POST method.
	 *
	 * @param donation the entity to be inserted.
	 * @return The inserted entity.
	 */
	@ApiMethod(name = "insertDonation")
	public Donation insertDonation(Donation donation) {
		PersistenceManager mgr = getPersistenceManager();
		try {
			if (containsDonation(donation)) {
				throw new EntityExistsException("Object already exists");
			}
			mgr.makePersistent(donation);
		} finally {
			mgr.close();
		}
		return donation;
	}

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param donation the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateDonation")
	public Donation updateDonation(Donation donation) {
		PersistenceManager mgr = getPersistenceManager();
		try {
			if (!containsDonation(donation)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			mgr.makePersistent(donation);
		} finally {
			mgr.close();
		}
		return donation;
	}

	/**
	 * This method removes the entity with primary key id.
	 * It uses HTTP DELETE method.
	 *
	 * @param id the primary key of the entity to be deleted.
	 * @return The deleted entity.
	 */
	@ApiMethod(name = "removeDonation")
	public Donation removeDonation(@Named("id") Long id) {
		PersistenceManager mgr = getPersistenceManager();
		Donation donation = null;
		try {
			donation = mgr.getObjectById(Donation.class, id);
			mgr.deletePersistent(donation);
		} finally {
			mgr.close();
		}
		return donation;
	}

	private boolean containsDonation(Donation donation) {
		PersistenceManager mgr = getPersistenceManager();
		boolean contains = true;
		try {
			mgr.getObjectById(Donation.class, donation.getId());
		} catch (javax.jdo.JDOObjectNotFoundException ex) {
			contains = false;
		} finally {
			mgr.close();
		}
		return contains;
	}

	private static PersistenceManager getPersistenceManager() {
		return PMF.get().getPersistenceManager();
	}

}
