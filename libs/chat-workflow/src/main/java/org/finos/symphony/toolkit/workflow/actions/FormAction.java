package org.finos.symphony.toolkit.workflow.actions;

import java.util.Map;

import org.finos.symphony.toolkit.workflow.content.Addressable;
import org.finos.symphony.toolkit.workflow.content.User;

/**
 * Created when the user submits a form in the underlying chat platform.
 * 
 * @author Rob Moffat
 *
 */
public class FormAction implements Action {

	private final Object formData;
	private final String action;
	private final Map<String, Object> entityJson;
	private final Addressable a;
	private final User u;
	
	public FormAction(Addressable a, User u, Object formData, String action, Map<String, Object> entityJson) {
		super();
		this.formData = formData;
		this.action = action;
		this.entityJson = entityJson;
		this.a = a;
		this.u = u;
	}

	public Object getFormData() {
		return formData;
	}

	public String getAction() {
		return action;
	}

	@Override
	public Map<String, Object> getData() {
		return entityJson;
	}

	@Override
	public String toString() {
		return "ElementsAction [formData=" + formData + ", action=" + action
				+ ", entityJson=" + entityJson + "]";
	}

	@Override
	public Addressable getAddressable() {
		return a;
	}

	@Override
	public User getUser() {
		return u;
	}

	
}
