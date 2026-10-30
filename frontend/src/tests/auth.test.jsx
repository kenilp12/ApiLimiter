import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { LoginPage } from '../pages/LoginPage';
import { AuthProvider } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';

describe('LoginPage Component', () => {
  it('renders login form inputs and submit button', () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <LoginPage />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    );

    expect(screen.getByPlaceholderText(/admin or developer/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/••••••••/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('autofills admin credentials when quick button is clicked', () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <LoginPage />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    );

    const adminButton = screen.getByRole('button', { name: /admin \(admin123\)/i });
    fireEvent.click(adminButton);

    const userInput = screen.getByPlaceholderText(/admin or developer/i);
    expect(userInput.value).toBe('admin');
  });
});
