import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Navbar from '../components/Navbar';

describe('Navbar', () => {
  it('renders navigation links for guests', () => {
    render(
      <MemoryRouter>
        <Navbar />
      </MemoryRouter>
    );

    // Dashboard link should always be visible
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument();

    // When no user is set, show the login link
    expect(screen.getByRole('link', { name: /entrar/i })).toBeInTheDocument();
  });
});

