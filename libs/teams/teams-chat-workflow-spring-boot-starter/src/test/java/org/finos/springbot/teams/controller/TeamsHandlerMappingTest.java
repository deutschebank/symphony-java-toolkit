package org.finos.springbot.teams.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.finos.springbot.teams.MockTeamsConfiguration;
import org.finos.springbot.teams.TeamsWorkflowConfig;
import org.finos.springbot.teams.content.TeamsChannel;
import org.finos.springbot.teams.content.TeamsChat;
import org.finos.springbot.teams.content.TeamsMultiwayChat;
import org.finos.springbot.teams.content.TeamsUser;
import org.finos.springbot.teams.conversations.TeamsConversations;
import org.finos.springbot.teams.messages.FileActivityHandler;
import org.finos.springbot.teams.state.TeamsStateStorage;
import org.finos.springbot.teams.turns.CurrentTurnContext;
import org.finos.springbot.tests.controller.AbstractHandlerMappingTest;
import org.finos.springbot.tests.controller.OurController;
import org.finos.springbot.workflow.actions.Action;
import org.finos.springbot.workflow.actions.SimpleMessageAction;
import org.finos.springbot.workflow.annotations.ChatRequest;
import org.finos.springbot.workflow.annotations.WorkMode;
import org.finos.springbot.workflow.content.Message;
import org.finos.springbot.workflow.data.DataHandlerConfig;
import org.finos.springbot.workflow.data.EntityJsonConverter;
import org.finos.springbot.workflow.form.Button;
import org.finos.springbot.workflow.form.Button.Type;
import org.finos.springbot.workflow.form.ButtonList;
import org.finos.springbot.workflow.java.mapping.ChatMapping;
import org.finos.springbot.workflow.java.mapping.ChatRequestChatHandlerMapping;
import org.finos.springbot.workflow.response.WorkResponse;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ConversationAccount;
import com.microsoft.bot.schema.Entity;
import com.microsoft.bot.schema.Mention;
import com.microsoft.bot.schema.ResourceResponse;
import com.microsoft.bot.schema.teams.ChannelInfo;
import com.microsoft.bot.schema.teams.FileConsentCard;
import com.microsoft.bot.schema.teams.TeamsChannelData;


@SpringBootTest(classes = {
		MockTeamsConfiguration.class, 
		TeamsWorkflowConfig.class,
		DataHandlerConfig.class
})
@ActiveProfiles("teams")
public class TeamsHandlerMappingTest extends AbstractHandlerMappingTest {
	
	ArgumentCaptor<Activity> msg;
	ArgumentCaptor<Map<String, Object>> data;
	
	TurnContext tc;
	
	@Autowired
	FileActivityHandler mah;
	
	@Autowired
	ChatRequestChatHandlerMapping hm;
	
	@MockBean
	TeamsStateStorage th;
	
	@MockBean
	TeamsConversations conv;
	
	@Autowired
	EntityJsonConverter ejc;
	
    public static void compareJson(String loadJson, String json) throws JsonMappingException, JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        Assertions.assertEquals(om.readTree(loadJson), om.readTree(json));
    }


	protected WorkResponse createWorkAddSubmit(WorkMode wm, Object ob5) {
		tc = Mockito.mock(TurnContext.class);
		CurrentTurnContext.CURRENT_CONTEXT.set(tc);
		
		msg = ArgumentCaptor.forClass(Activity.class);
		Mockito.when(tc.sendActivity(msg.capture())).thenReturn(CompletableFuture.completedFuture(null));	
				
		TeamsMultiwayChat theRoom = new TeamsMultiwayChat( "abc123", "tesxt room");
		WorkResponse wr = new WorkResponse(theRoom, ob5, wm);
		ButtonList bl = (ButtonList) wr.getData().get(ButtonList.KEY);
		Button submit = new Button("submit", Type.ACTION, "GO");
		bl.add(submit);
		return wr;
	}
	

	@SuppressWarnings("unchecked")
	@Override
	protected String getMessageData() {
		data = ArgumentCaptor.forClass(Map.class);
		Mockito.verify(th).store(Mockito.any(), Mockito.any(), data.capture());

		return ejc.writeValue(data.getValue());
	}


	@Override
	protected String getMessageContent() {
		msg = ArgumentCaptor.forClass(Activity.class);
		Mockito.verify(conv).handleActivity(msg.capture(), Mockito.any());
		Activity out = msg.getValue();
		if (out.getAttachments().size() > 0) {
			Attachment a1 = out.getAttachments().get(0);
			
			if (a1.getContent() instanceof String) {
				return (String) a1.getContent();
			} else if (a1.getContent() instanceof FileConsentCard) {
				FileConsentCard f = (FileConsentCard)(a1.getContent());
				Map<String, String> acceptContext = (Map<String, String>) f.getAcceptContext();
				return acceptContext.get("filename");
				
			}else if (a1.getContent() instanceof ObjectNode) {
				try {
					return new ObjectMapper().writeValueAsString(a1.getContent());
				} catch (JsonProcessingException e) {
					throw new RuntimeException("Couldn't get JSON", e);
				}
			} else {
				throw new RuntimeException("Can't figure out message content");
			}
			
			
		} else {
			return out.getText();
		}
	}


	@Override
	protected void execute(String s) throws Exception {
		oc.lastMethod = null;
		s = s.replace("@gaurav", "<span itemscope=\"\" itemtype=\"http://schema.skype.com/Mention\" itemid=\"1\">gaurav</span>");
		s = "<span itemscope=\"\" itemtype=\"http://schema.skype.com/Mention\" itemid=\"0\">"+BOT_NAME+"</span>" + s;
		
		mockSetup();
		mockTurnContext(s, null, false);
		mah.handleActivity(tc);
	}
	
	private void mockSetup() {
		Mockito.when(conv.getUser(Mockito.any())).thenAnswer(iom -> {
			ChannelAccount ca = (ChannelAccount) iom.getArgument(0);
			if (ca.getName().equals(ROB_NAME)) {
				return new TeamsUser("directchatid", ROB_NAME, ROB_EXAMPLE_EMAIL);
			} else if (ca.getName().equals(BOT_NAME)) {
				return new TeamsUser(""+BOT_ID, BOT_NAME, BOT_EMAIL);
			}
			
			return null;
		});
		
		Mockito.when(conv.isSupported(Mockito.any(TeamsChat.class))).thenReturn(true);
		Mockito.when(conv.isSupported(Mockito.any(TeamsUser.class))).thenReturn(true);

		
		Mockito.when(conv.getTeamsAddressable(Mockito.any()))
			.thenReturn(new TeamsChannel(CHAT_ID, OurController.SOME_ROOM));
	
		Mockito.when(conv.getChatAdmins(Mockito.any()))
			.thenAnswer(iom -> Arrays.asList( new TeamsUser("directchatid", ROB_NAME, ROB_EXAMPLE_EMAIL)));
		
		msg = ArgumentCaptor.forClass(Activity.class);
		Mockito.when(conv.handleActivity(msg.capture(), Mockito.any()))
			.thenAnswer(a -> {
				ResourceResponse arg1 = new ResourceResponse("blah");
				Throwable arg2 = null;
				
				return new CompletableFuture<ResourceResponse>() {
					
					@Override
					public <U> CompletableFuture<U> handle(
							BiFunction<? super ResourceResponse, Throwable, ? extends U> fn) {
						U out;
						try {
							out = fn.apply(arg1, arg2);
							return CompletableFuture.completedFuture(out);
						} catch (Throwable e) {
							return failed(e);
						} 
					}
					
				};
			});	
				
		Mockito.when(th.createStorageId()).thenReturn("abc123");
	}

	public static <R> CompletableFuture<R> failed(Throwable error) {
	    CompletableFuture<R> future = new CompletableFuture<>();
	    future.completeExceptionally(error);
	    return future;
	}
	
	private void mockTurnContext(String s, Map<String, Object> formData, boolean isAttachement) {
		tc = Mockito.mock(TurnContext.class);
		CurrentTurnContext.CURRENT_CONTEXT.set(tc);

		msg = ArgumentCaptor.forClass(Activity.class);
		Mockito.when(tc.sendActivity(msg.capture())).thenReturn(CompletableFuture.completedFuture(null));
		
		Activity out = createActivity(s, formData, isAttachement);
		Mockito.when(tc.getActivity()).thenReturn(out);
	}


	private Activity createActivity(String s, Map<String, Object> formData, boolean isAttachement) {
		Activity out = new Activity(ActivityTypes.MESSAGE);
		
		ConversationAccount conv = new ConversationAccount(CHAT_ID);
		out.setConversation(conv);
		conv.setConversationType("channel");
		conv.setName(OurController.SOME_ROOM);
		
		TeamsChannelData tcd = new TeamsChannelData();
		ChannelInfo ci = new ChannelInfo(CHAT_ID, OurController.SOME_ROOM);
		tcd.setChannel(ci);
		out.setChannelData(tcd);
		
		ChannelAccount ca = new ChannelAccount(""+ROB_EXAMPLE_ID, ROB_NAME);
		out.setFrom(ca);
		
		ChannelAccount to = botChannelAccount();
		out.setRecipient(to);
		
		out.setEntities(Arrays.asList(botEntity(), gauravEntity()));
		
		
		if(isAttachement) {
			Attachment a = new Attachment();
			String fileName = s;
			Map<String, String> consentContext = new HashMap<>();
			consentContext.put("filename", fileName);
			FileConsentCard fileCard = new FileConsentCard();
			fileCard.setDescription("This is the file I want to send you");			
			fileCard.setAcceptContext(consentContext);
			fileCard.setDeclineContext(consentContext);

			a.setContent(fileCard);
			a.setName(fileName);
			a.setContentType(FileConsentCard.CONTENT_TYPE);
			out.setAttachment(a);
		} else if (formData != null) {
			formData.put("action", s);
			out.setValue(formData);
		} else {
			Attachment a = new Attachment();
			a.setContentType(MediaType.TEXT_HTML_VALUE);
			a.setContent("<div>"+s+"</div>");
			out.setAttachment(a);
		}

		
		return out;
	}


	private ChannelAccount botChannelAccount() {
		return new ChannelAccount(""+BOT_ID, BOT_NAME);
	}


	private Entity gauravEntity() {
		Mention out = new Mention();
		out.setText("<at>gaurav</at>");
		ChannelAccount ca = new ChannelAccount();
		ca.setName("gaurav");
		ca.setId("3276423876");
		out.setMentioned(ca);
		return new Entity().setAs(out);
	}
	
	private Entity botEntity() {
		Mention out = new Mention();
		out.setText("<at>"+BOT_NAME+"</at>");
		ChannelAccount ca = new ChannelAccount();
		ca.setName(BOT_NAME);
		ca.setId(""+BOT_ID);
		out.setMentioned(ca);
		return new Entity().setAs(out);
	}


	@Override
	protected void pressButton(String s, Map<String, Object> formData) {
		mockSetup();
		mockTurnContext(s, formData, false);
		mah.handleActivity(tc);
	}


	@Override
	protected List<ChatMapping<ChatRequest>> getMappingsFor(Message s) throws Exception {
		Map<String, Object> map = new HashMap<>();
 		Action a = new SimpleMessageAction(null, null, s, map);
		return hm.getHandlers(a);
	}


	@Override
	protected void assertHelpResponse() throws Exception {
		String data = getMessageContent();
		System.out.println(data);
		Assertions.assertTrue(data.contains("<pre style=\"display:inline\" >optionals {thing} {user} {lastword}</pre>"));
	}


	@Override
	protected void assertNoButtons() {
		String data = getMessageData();
		Assertions.assertFalse(data.contains("ActionSet"));
	}


	@Override
	protected void assertThrowsResponse() {
		String message = getMessageContent();
		Assertions.assertTrue(message.contains("Error123"));
	}


}
