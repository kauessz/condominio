import { useState, useEffect } from 'react';

interface CurrentUser {
  id?: number;
  email: string;
  name: string;
  role: string;
  tenant: string;
  unitId?: number;
}

export function useCurrentUser() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const token = localStorage.getItem('token');
        const tenant = localStorage.getItem('tenant') || 'demo';

        if (!token) {
          setError('No token found');
          setLoading(false);
          return;
        }

        const response = await fetch('http://localhost:8080/api/auth/me', {
          headers: {
            'Authorization': `Bearer ${token}`,
            'X-Tenant': tenant,
          },
        });

        if (!response.ok) {
          throw new Error('Failed to fetch user data');
        }

        const data = await response.json();
        setUser(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error');
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, []);

  return { user, loading, error };
}