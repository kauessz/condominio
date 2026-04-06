package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.user.UserResponse;
import com.example.condo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Test
    void shouldReturnPaginatedUserListing() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.list("ana", 0, 20)).thenReturn(
            PageResponse.of(
                List.of(new UserResponse(1L, "Ana", "ana@test.com", "ADMIN", "Administrador", null, 10L, null, null, false, null, null)),
                0,
                20,
                1
            )
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();

        mockMvc.perform(get("/users").param("q", "ana").param("page", "0").param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].email").value("ana@test.com"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));

        verify(userService).list("ana", 0, 20);
    }
}
