package com.coronacarecard.controller;

import com.coronacarecard.dao.BusinessRepository;
import com.coronacarecard.dao.entity.Business;
import com.coronacarecard.model.BusinessSearchResult;
import com.coronacarecard.model.PagedBusinessSearchResult;
import com.coronacarecard.util.RepoUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.UnsupportedEncodingException;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
public class BusinessControllerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());
    private static final String whatThePhoId = "ChIJicMwN4lskFQR9brCQh07Xyo";

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessController businessController;

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void init() {
        String idPreix = "78255b5db1ca027c669ca49e9576d7a26b40f7a";
        for (int i = 0; i < 10; i++) {
            RepoUtil.createEntry(businessRepository,  "773773773",
                    idPreix + i, "RandomName" + i);

        }
    }

    @Test
    public void search_for_existing() throws Exception {
        MvcResult response = mockMvc.perform(MockMvcRequestBuilders.get("/business/search?count=5&page=1&searchtext=RandomName")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andReturn();
        PagedBusinessSearchResult result = parseResponse(response, PagedBusinessSearchResult.class);
        assertEquals(2, result.getTotalPages().intValue());
    }

    @Test
    public void search_for_non_existing() throws Exception {
        MvcResult response = mockMvc.perform(MockMvcRequestBuilders.get("/business/search?count=5&page=1&searchtext=what the pho")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andReturn();
        PagedBusinessSearchResult result = parseResponse(response, PagedBusinessSearchResult.class);
        assertEquals(0, result.getTotalPages().intValue());
        assertEquals(0, result.getItems().size());

    }

    @Test
    public void search_for_external_system() throws Exception {
        MvcResult response = mockMvc.perform(MockMvcRequestBuilders.get("/business/searchexternal?searchtext=what the pho")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andReturn();
        BusinessSearchResult[] result = parseResponse(response, BusinessSearchResult[].class);
        assertEquals(1, result.length);
        assertEquals("What The Pho", result[0].getName());
        assertEquals(whatThePhoId, result[0].getId());

    }

    @Test
    public void import_for_external_system() throws Exception {
        Optional<Business> beforeImport = businessRepository.findById(whatThePhoId);
        assertFalse(beforeImport.isPresent());
        MvcResult response = mockMvc.perform(MockMvcRequestBuilders.get("/business/import?googleplaceid="+whatThePhoId)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andReturn();
        Optional<Business> afterImport = businessRepository.findById(whatThePhoId);
        assertTrue(afterImport.isPresent());
        assertEquals("", afterImport.get().getAddress());


    }

    public static <T> T parseResponse(MvcResult result, Class<T> responseClass)
            throws UnsupportedEncodingException, JsonProcessingException {
        String contentAsString = result.getResponse().getContentAsString();
        return MAPPER.readValue(contentAsString, responseClass);
    }

}