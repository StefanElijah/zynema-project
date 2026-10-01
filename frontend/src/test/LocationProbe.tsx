import { useLocation } from 'react-router-dom';

/** Renders the current path and query, for navigation assertions. */
export default function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{`${location.pathname}${location.search}`}</span>;
}
